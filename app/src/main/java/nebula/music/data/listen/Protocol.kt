package nebula.music.data.listen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

private val snakeCaseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
}

/**
 * Wire protocol for Listen Together (community server, wss://metroserverx.meowery.eu/ws).
 *
 * Every frame is one JSON object: `{"type": "...", "payload": {...}}`.
 * The payload is a NESTED JSON object — never a string, never Protobuf.
 * An earlier attempt wrapped JSON text inside Protobuf envelopes; the server
 * could not decode it and the UI showed "message format" / "did not respond".
 *
 * Kotlin property names are kept for existing callers; wire keys that differ
 * from the snake-cased name are pinned with @SerialName.
 */

val listenJson = snakeCaseJson

/** Message types the server understands — lowercase, underscore-separated. */
object MessageTypes {
    // client -> server
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val PING = "ping"

    // server -> client
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val SYNC_PLAYBACK = "sync_playback" // server relays host actions to guests
    const val SYNC_STATE = "sync_state"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val PONG = "pong"
    const val ERROR = "error"
}

/** Playback actions that can be synced. */
object PlaybackActions {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val CHANGE_TRACK = "change_track"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
}

/** The role a user has in a room. */
enum class RoomRole { HOST, GUEST, NONE }

/** One wire frame. `payload` is the nested JSON object, or null for bare frames (ping). */
@Serializable
data class ListenMessage(
    val type: String = "",
    val payload: JsonElement? = null
)

/** A song in the room. Wire keys: id, title, artist, album, duration, thumbnail, suggested_by. */
@Serializable
data class TrackInfo(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    @SerialName("duration") val durationMs: Long = 0L,
    @SerialName("thumbnail") val thumbnailUrl: String? = null,
    @SerialName("suggested_by") val suggestedBy: String? = ""
)

/** A user in the room. */
@Serializable
data class UserInfo(
    @SerialName("user_id") val userId: String = "",
    val username: String = "",
    @SerialName("is_host") val isHost: Boolean = false
)

/** Full room state; sent inside join_approved and as a periodic sync_state refresh. */
@Serializable
data class RoomState(
    @SerialName("room_code") val roomCode: String = "",
    @SerialName("host_id") val hostId: String = "",
    val users: List<UserInfo> = emptyList(),
    @SerialName("current_track") val currentTrack: TrackInfo? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("position") val positionMs: Long = 0L,
    @SerialName("last_update") val lastUpdateMs: Long = 0L,
    val volume: Float = 1.0f,
    val queue: List<TrackInfo> = emptyList(),
    @SerialName("allow_participant_control") val allowParticipantControl: Boolean = false
)

/** Host playback action, relayed to guests as sync_playback. */
@Serializable
data class PlaybackActionPayload(
    val action: String = "",
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("position") val positionMs: Long? = null,
    @SerialName("track_info") val trackInfo: TrackInfo? = null,
    val queue: List<TrackInfo>? = null,
    val volume: Float? = null,
    @SerialName("server_time") val serverTime: Long? = null
)

/** Payload for creating a room — the only field the server needs. */
@Serializable
data class CreateRoomPayload(
    val username: String = ""
)

/** Server reply to create_room. */
@Serializable
data class RoomCreatedPayload(
    @SerialName("room_code") val roomCode: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("session_token") val sessionToken: String = ""
)

/** Payload for joining a room. */
@Serializable
data class JoinRoomPayload(
    val roomCode: String = "",
    val username: String = ""
)

/** Server tells the host someone wants to join. */
@Serializable
data class JoinRequestPayload(
    @SerialName("user_id") val userId: String = "",
    val username: String = ""
)

/** Host approves a join request. */
@Serializable
data class ApproveJoinPayload(
    @SerialName("user_id") val userId: String = ""
)

/** Host declines a join request. */
@Serializable
data class RejectJoinPayload(
    @SerialName("user_id") val userId: String = "",
    val reason: String = ""
)

/** Server reply to join_room after the host approves — full state is nested in `state`. */
@Serializable
data class JoinApprovedPayload(
    @SerialName("room_code") val roomCode: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("session_token") val sessionToken: String = "",
    val state: RoomState = RoomState()
)

/** Server reply to join_room when rejected — plain reason string. */
@Serializable
data class JoinRejectedPayload(
    val reason: String = ""
)

/** Server error — code + human message. */
@Serializable
data class ErrorPayload(
    val code: String = "",
    val message: String = ""
)

/** Someone joined the room. */
@Serializable
data class UserJoinedPayload(
    @SerialName("user_id") val userId: String = "",
    val username: String = ""
)

/** Someone left the room. */
@Serializable
data class UserLeftPayload(
    @SerialName("user_id") val userId: String = "",
    val username: String = ""
)

/** Server tells the room a new host took over. */
@Serializable
data class HostChangedPayload(
    @SerialName("new_host_id") val newHostId: String = "",
    @SerialName("new_host_name") val newHostName: String = ""
)

/** Periodic state refresh the server pushes to guests. */
@Serializable
data class SyncStatePayload(
    @SerialName("current_track") val currentTrack: TrackInfo? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("position") val positionMs: Long = 0L,
    @SerialName("last_update") val lastUpdateMs: Long = 0L,
    val queue: List<TrackInfo> = emptyList(),
    val volume: Float? = null
)

/** Encode a typed message: {"type":"...","payload":{...}} with the payload nested. */
inline fun <reified T> encodeMessage(type: String, payload: T): String {
    val element = listenJson.encodeToJsonElement(kotlinx.serialization.serializer<T>(), payload)
    return listenJson.encodeToString(ListenMessage.serializer(), ListenMessage(type, element))
}

/** Encode a bare frame with no payload (ping). */
fun encodeMessage(type: String): String =
    listenJson.encodeToString(ListenMessage.serializer(), ListenMessage(type))

/** Parse a raw JSON frame into a ListenMessage. */
fun parseMessage(raw: String): ListenMessage? {
    return runCatching {
        listenJson.decodeFromString(ListenMessage.serializer(), raw)
    }.getOrNull()
}

/** Decode a frame's nested payload into a typed message. Null if absent or malformed. */
inline fun <reified T> ListenMessage.payloadOf(): T? =
    payload?.let { element ->
        runCatching { listenJson.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), element) }
            .getOrNull()
    }
