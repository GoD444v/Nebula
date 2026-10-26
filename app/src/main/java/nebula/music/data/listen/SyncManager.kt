package nebula.music.data.listen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nebula.music.data.listen.ProtoWire

/**
 * Sync manager — the brain of Listen Together.
 *
 * Host side: watches the local player and sends playback actions to the server.
 * Guest side: receives playback actions and applies them to the local player.
 *
 * Echo-style drift correction: the host sends a heartbeat every 10 seconds.
 * The guest only seeks if drift exceeds 2 seconds — smaller gaps are left alone
 * to avoid micro-stuttering.
 */
class SyncManager(
    private val client: ListenClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
) {
    var role: RoomRole = RoomRole.NONE
    private var heartbeatJob: Job? = null
    private var clockSyncJob: Job? = null

    // Sent timestamp of the last keep-alive ping; used to measure RTT on pong.
    private var lastPingSentAtMs: Long = 0L
    /** Round-trip time of the latest ping/pong pair, 0 until the first pong. */
    var lastRttMs: Long = 0L
        private set

    // Last known host state, for drift calculation on the guest side.
    private var lastHostPositionMs: Long = 0L
    private var lastHostServerTimeMs: Long = 0L
    private var lastHostIsPlaying: Boolean = false

    // Guest side: the player layer applies host actions locally (wired by PlayerViewModel).
    var onApplyPlaybackAction: ((PlaybackActionPayload) -> Unit)? = null

    // Host side: push the current track to a newly joined user (wired by PlayerViewModel).
    var onSendCurrentTrack: (() -> Unit)? = null

    // Room state that survives the Listen screen leaving composition, so
    // re-entering it still shows the room instead of the create/join form.
    var inRoom: Boolean = false
    var roomCode: String? = null
    var roomUsers: List<String> = emptyList()

    fun clearRoom() {
        inRoom = false
        roomCode = null
        roomUsers = emptyList()
    }

    // Join requests the host must answer. Shown by the Listen screen; lives here
    // so a request survives the host backing out of the screen while in a room.
    val joinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())

    fun removeJoinRequest(userId: String) {
        joinRequests.value = joinRequests.value.filter { it.userId != userId }
    }

    /**
     * Start as host — watch the local player and broadcast actions.
     */
    fun startAsHost() {
        role = RoomRole.HOST
        startClockSync()
        startHeartbeat()
        listenForMessages()
    }

    /**
     * Start as guest — listen for host actions and apply them locally.
     */
    fun startAsGuest() {
        role = RoomRole.GUEST
        startClockSync()
        listenForMessages()
    }

    /**
     * Stop all sync activity.
     */
    fun stop() {
        role = RoomRole.NONE
        heartbeatJob?.cancel()
        clockSyncJob?.cancel()
    }

    /**
     * Host: call this whenever the local player state changes.
     */
    fun hostPlaybackChanged(
        action: String,
        positionMs: Long,
        trackInfo: TrackInfo? = null,
        queue: List<TrackInfo>? = null,
        volume: Float? = null
    ) {
        if (role != RoomRole.HOST) return
        val hostTimestamp = System.currentTimeMillis()
        val payload = PlaybackActionPayload(
            action = action,
            trackId = trackInfo?.id,
            positionMs = positionMs,
            trackInfo = trackInfo,
            queue = queue,
            volume = volume,
            serverTime = hostTimestamp
        )
        // Both dialects ready; the client picks JSON normally, Protobuf once the server upgrades.
        client.sendFrame(
            MessageTypes.PLAYBACK_ACTION,
            json = { encodeMessage(MessageTypes.PLAYBACK_ACTION, payload) },
            proto = {
                ProtoWire.encodePlaybackAction(
                    action = action,
                    trackId = trackInfo?.id,
                    positionMs = positionMs,
                    trackInfo = trackInfo,
                    queue = queue,
                    volume = volume,
                    serverTime = hostTimestamp
                )
            }
        )
    }

    /**
     * Guest: compute the expected host position right now, accounting for
     * network delay. Returns null if we don't have enough data yet.
     */
    fun expectedHostPositionMs(): Long? {
        if (lastHostServerTimeMs == 0L) return null
        val now = System.currentTimeMillis()
        val elapsed = now - lastHostServerTimeMs
        return if (lastHostIsPlaying) {
            lastHostPositionMs + elapsed
        } else {
            lastHostPositionMs
        }
    }

    /**
     * Guest: check if local playback has drifted too far from the host.
     * Returns true if a seek is needed.
     */
    fun needsDriftCorrection(localPositionMs: Long): Boolean {
        val expected = expectedHostPositionMs() ?: return false
        val drift = kotlin.math.abs(localPositionMs - expected)
        return drift > 2000L // 2-second threshold
    }

    // --- Private ---

    private fun listenForMessages() {
        scope.launch {
            client.messages.collectLatest { raw ->
                val msg = parseMessage(raw) ?: return@collectLatest
                when (msg.type) {
                    MessageTypes.SYNC_PLAYBACK -> handlePlaybackAction(msg)
                    MessageTypes.SYNC_STATE -> handleSyncState(msg)
                    MessageTypes.ROOM_CREATED -> handleRoomCreated(msg)
                    MessageTypes.JOIN_APPROVED -> handleJoinApproved(msg)
                    MessageTypes.JOIN_REJECTED -> handleJoinRejected(msg)
                    MessageTypes.JOIN_REQUEST -> handleJoinRequest(msg)
                    MessageTypes.USER_LEFT -> handleUserLeft(msg)
                    MessageTypes.HOST_CHANGED -> handleHostChanged(msg)
                    MessageTypes.PONG -> handlePong()
                    else -> { /* sync frames handled by the player layer */ }
                }
            }
        }
    }

    private fun handlePlaybackAction(msg: ListenMessage) {
        if (role != RoomRole.GUEST) return
        val payload = msg.payloadOf<PlaybackActionPayload>() ?: return

        lastHostPositionMs = payload.positionMs ?: 0L
        lastHostServerTimeMs = payload.serverTime ?: 0L
        lastHostIsPlaying = payload.action == PlaybackActions.PLAY

        onApplyPlaybackAction?.invoke(payload)
    }

    private fun handleSyncState(msg: ListenMessage) {
        val state = msg.payloadOf<SyncStatePayload>() ?: return
        lastHostPositionMs = state.positionMs
        lastHostServerTimeMs = System.currentTimeMillis()
        lastHostIsPlaying = state.isPlaying
    }

    private fun handleRoomCreated(msg: ListenMessage) {
        // The UI layer will use this to show the room code
    }

    private fun handleJoinApproved(msg: ListenMessage) {
        val payload = msg.payloadOf<JoinApprovedPayload>() ?: return
        lastHostPositionMs = payload.state.positionMs
        lastHostServerTimeMs = System.currentTimeMillis()
        lastHostIsPlaying = payload.state.isPlaying
        // Apply the room's state right away: switch to the host's track, then
        // match play/pause. Guests would otherwise keep their own song playing.
        payload.state.currentTrack?.let { track ->
            onApplyPlaybackAction?.invoke(
                PlaybackActionPayload(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = track.id,
                    positionMs = payload.state.positionMs.takeIf { it > 0 },
                    trackInfo = track,
                    queue = payload.state.queue.takeIf { it.isNotEmpty() },
                    serverTime = payload.state.lastUpdateMs.takeIf { it > 0 }
                )
            )
            if (payload.state.isPlaying) {
                onApplyPlaybackAction?.invoke(
                    PlaybackActionPayload(
                        action = PlaybackActions.PLAY,
                        positionMs = payload.state.positionMs.takeIf { it > 0 },
                        serverTime = payload.state.lastUpdateMs.takeIf { it > 0 }
                    )
                )
            }
        }
    }

    private fun handleJoinRejected(msg: ListenMessage) {
        // The screen collects join_rejected itself and shows the reason
        msg.payloadOf<JoinRejectedPayload>()
    }

    private fun handleJoinRequest(msg: ListenMessage) {
        val payload = msg.payloadOf<JoinRequestPayload>() ?: return
        if (joinRequests.value.none { it.userId == payload.userId }) {
            joinRequests.value = joinRequests.value + payload
        }
        // A guest joining mid-playback needs the room's current state immediately;
        // the periodic sync alone leaves them on their own song.
        if (role == RoomRole.HOST) onSendCurrentTrack?.invoke()
    }

    private fun handleUserLeft(msg: ListenMessage) {
        val payload = msg.payloadOf<UserLeftPayload>() ?: return
        // A user who left can no longer have an open join request
        joinRequests.value = joinRequests.value.filter { it.userId != payload.userId }
    }

    private fun handleHostChanged(msg: ListenMessage) {
        // Host changes don't affect sync bookkeeping here
        msg.payloadOf<HostChangedPayload>()
    }

    private fun handlePong() {
        if (lastPingSentAtMs == 0L) return
        lastRttMs = System.currentTimeMillis() - lastPingSentAtMs
        lastPingSentAtMs = 0L
    }

    private fun startClockSync() {
        if (clockSyncJob?.isActive == true) return
        clockSyncJob = scope.launch {
            while (isActive) {
                // Bare keep-alive ping: {"type":"ping"} with no payload. The
                // server answers with pong; we only measure RTT from it.
                lastPingSentAtMs = System.currentTimeMillis()
                client.sendFrame(
                    MessageTypes.PING,
                    json = { encodeMessage(MessageTypes.PING) },
                    proto = { ProtoWire.encodePing() }
                )
                delay(25000)
            }
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(10_000) // 10-second heartbeat
                if (role == RoomRole.HOST) {
                    // The UI layer should call hostPlaybackChanged with current state
                    // This is a fallback to keep guests in sync
                }
            }
        }
    }
}
