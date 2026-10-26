package nebula.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nebula.music.data.listen.ListenClient
import nebula.music.data.listen.MessageTypes
import nebula.music.data.listen.CreateRoomPayload
import nebula.music.data.listen.JoinRoomPayload
import nebula.music.data.listen.SyncManager
import nebula.music.data.listen.encodeMessage
import nebula.music.data.listen.payloadOf
import nebula.music.data.listen.ProtoWire
import nebula.music.data.listen.RoomCreatedPayload
import nebula.music.data.listen.JoinApprovedPayload
import nebula.music.data.listen.JoinRejectedPayload
import nebula.music.data.listen.ErrorPayload
import nebula.music.data.listen.UserJoinedPayload
import nebula.music.data.listen.UserLeftPayload
import nebula.music.data.listen.ApproveJoinPayload
import nebula.music.data.listen.RejectJoinPayload
import nebula.music.data.listen.JoinRequestPayload
import nebula.music.ui.theme.BorderBlack
import nebula.music.ui.theme.TextGrey

@Composable
fun ListenTogetherScreen(
    client: ListenClient,
    syncManager: SyncManager,
    onBack: () -> Unit
) {
    var roomCode by remember { mutableStateOf(syncManager.roomCode) }
    var joinCode by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    val isInRoomState = remember { mutableStateOf(syncManager.inRoom) }
    var isInRoom by isInRoomState
    var users by remember { mutableStateOf(syncManager.roomUsers) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    // What we're waiting on — "create" (server answers instantly) or "join"
    // (a human host has to approve, so no timeout applies).
    var pendingAction by remember { mutableStateOf("") }
    var connectionTimeout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Backing out with no room must drop the socket: the server keeps a pending
    // join request as long as the connection lives, and an orphaned connection
    // would answer the NEXT join with "you already have a pending join request".
    DisposableEffect(Unit) {
        onDispose {
            if (!isInRoomState.value) {
                syncManager.stop()
                client.disconnect()
            }
        }
    }

    // Create gets a 10s watchdog. Join deliberately has none: quitting the wait
    // and re-tapping sent a second join_room under the still-pending first one.
    LaunchedEffect(isConnecting, pendingAction) {
        if (isConnecting && pendingAction == "create") {
            connectionTimeout = false
            kotlinx.coroutines.delay(10_000)
            if (isConnecting) {
                connectionTimeout = true
                isConnecting = false
                pendingAction = ""
                errorMessage = "Server did not respond. Try again."
            }
        }
    }

    // Listen for server responses — collect in a LaunchedEffect so state updates
    // reach the UI. MainScope().launch was fire-and-forget and never triggered recomposition.
    LaunchedEffect(client) {
        client.messages.collect { raw ->
            // Server may respond with Protobuf binary (first byte != '{') — don't crash
            if (raw.isNotEmpty() && raw[0] == '{') {
                val msg = nebula.music.data.listen.parseMessage(raw) ?: return@collect
                when (msg.type) {
                MessageTypes.ROOM_CREATED -> {
                    val payload = msg.payloadOf<RoomCreatedPayload>() ?: return@collect
                    roomCode = payload.roomCode
                    isInRoom = true
                    isConnecting = false
                    pendingAction = ""
                    users = listOf(username)
                    // Room state lives in the SyncManager so re-entering the
                    // screen still shows the room.
                    syncManager.inRoom = true
                    syncManager.roomCode = payload.roomCode
                    syncManager.roomUsers = users
                    // Host role starts the moment the room exists
                    syncManager.startAsHost()
                }
                MessageTypes.JOIN_APPROVED -> {
                    val payload = msg.payloadOf<JoinApprovedPayload>() ?: return@collect
                    roomCode = payload.roomCode.ifBlank { payload.state.roomCode }
                    isInRoom = true
                    isConnecting = false
                    pendingAction = ""
                    users = payload.state.users.map { it.username }
                    syncManager.inRoom = true
                    syncManager.roomCode = roomCode
                    syncManager.roomUsers = users
                    syncManager.startAsGuest()
                }
                MessageTypes.JOIN_REJECTED -> {
                    val payload = msg.payloadOf<JoinRejectedPayload>()
                    errorMessage = if (payload?.reason?.isBlank() != false) {
                        "Join request was rejected"
                    } else {
                        "Join request was rejected: ${payload.reason}"
                    }
                    isConnecting = false
                    pendingAction = ""
                }
                MessageTypes.USER_JOINED -> {
                    msg.payloadOf<UserJoinedPayload>()?.let { joined ->
                        if (joined.username.isNotBlank() && joined.username !in users) {
                            users = users + joined.username
                            syncManager.roomUsers = users
                        }
                    }
                }
                MessageTypes.USER_LEFT -> {
                    msg.payloadOf<UserLeftPayload>()?.let { left ->
                        users = users.filter { it != left.username }
                        syncManager.roomUsers = users
                        // A user who left can no longer have an open join request
                        left.userId.takeIf { it.isNotBlank() }?.let { syncManager.removeJoinRequest(it) }
                    }
                }
                MessageTypes.ERROR -> {
                    val payload = msg.payloadOf<ErrorPayload>()
                    errorMessage = payload?.message?.takeIf { it.isNotBlank() } ?: "Server error"
                    isConnecting = false
                    pendingAction = ""
                }
                else -> { /* sync frames are handled by SyncManager */ }
                }
            } else {
                // Protobuf binary response — show a generic error for now
                errorMessage = "Server rejected the message format"
                isConnecting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // Back button — chunky 3D Vox style
        Box(
            modifier = Modifier.clickable(onClick = onBack)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(3.dp, BorderBlack, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isInRoom) {
            // Header
            Text(
                "Listen Together",
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Create a room and share the code, or join a friend's room.",
                fontSize = 14.sp,
                color = TextGrey()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Create Room — chunky 3D button with shadow
            if (isConnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (pendingAction == "join") "Waiting for the host to approve…" else "Connecting…",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = TextGrey()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isConnecting = true
                            errorMessage = null
                            pendingAction = "create"
                            client.connect()
                            client.sendFrame(
                                MessageTypes.CREATE_ROOM,
                                json = { encodeMessage(MessageTypes.CREATE_ROOM, CreateRoomPayload(username = username)) },
                                proto = { ProtoWire.encodeCreateRoom(username) }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .offset(x = 4.dp, y = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CREATE ROOM", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("— or join a room —", fontSize = 12.sp, color = TextGrey())

            Spacer(modifier = Modifier.height(12.dp))

            // Join code field
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase() },
                label = { Text("Room code") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Join Room — chunky 3D button with shadow (locked while any request is in flight)
            val joinDisabled = isConnecting
            if (joinDisabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val waitingText =
                        if (pendingAction == "join") "Waiting for the host to approve…"
                        else "Connecting…"
                    Text(
                        waitingText,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = TextGrey()
                    )
                }
            } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (joinCode.isBlank()) {
                            errorMessage = "Enter the room code first"
                        } else {
                            isConnecting = true
                            errorMessage = null
                            pendingAction = "join"
                            client.connect()
                            client.sendFrame(
                                MessageTypes.JOIN_ROOM,
                                json = {
                                    encodeMessage(MessageTypes.JOIN_ROOM, JoinRoomPayload(roomCode = joinCode.trim(), username = username))
                                },
                                proto = { ProtoWire.encodeJoinRoom(joinCode.trim(), username) }
                            )
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("JOIN ROOM", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
            } // unlock join when nothing is pending

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMessage!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }
        } else {
            // In a room
            Text(
                "Room: $roomCode",
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "In the room:",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Join requests need a human decision — approve or decline each one
            val joinRequests by syncManager.joinRequests.collectAsState()
            joinRequests.forEach { request ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        "${request.username} wants to join",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clickable {
                                client.sendFrame(
                                    MessageTypes.APPROVE_JOIN,
                                    json = { encodeMessage(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId = request.userId)) },
                                    proto = { ProtoWire.encodeApproveJoin(request.userId) }
                                )
                                syncManager.removeJoinRequest(request.userId)
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, BorderBlack, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("APPROVE", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable {
                                client.sendFrame(
                                    MessageTypes.REJECT_JOIN,
                                    json = { encodeMessage(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId = request.userId, reason = "Request declined")) },
                                    proto = { ProtoWire.encodeRejectJoin(request.userId, "Request declined") }
                                )
                                syncManager.removeJoinRequest(request.userId)
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, BorderBlack, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("DENY", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            users.forEach { user ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                            .border(2.dp, BorderBlack, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(user, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Leave — chunky 3D button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        syncManager.stop()
                        syncManager.clearRoom()
                        client.disconnect()
                        isInRoom = false
                        roomCode = null
                        users = emptyList()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, BorderBlack, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LEAVE ROOM", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
    }
}
