package nebula.music.data.listen

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for Listen Together.
 *
 * Protocol dialect logic:
 * - The client OPENS conversations in JSON ({"type","payload":{...}}).
 * - If the server replies with a binary (Protobuf) frame once, the client
 *   upgrades permanently for THAT connection and sends Protobuf envelopes
 *   from then on. Incoming Protobuf is decoded to the same JSON shape at
 *   this boundary, so readers below see one dialect only.
 * - Mode resets on each new connection, mirroring the reference client.
 */
class ListenClient(
    private val serverUrl: String = "wss://metroserverx.meowery.eu/ws",
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var isConnecting = false
    private val messageQueue = mutableListOf<ByteArray>()

    @Volatile
    var protobufMode: Boolean = false
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    /** Connect to the server. Safe to call multiple times — only one connection at a time. */
    fun connect() {
        if (isConnecting || webSocket != null) return
        isConnecting = true
        connectionErrors = 0
        Log.d("NebulaListen", "Connecting to $serverUrl")
        scope.launch {
            _connectionState.emit(ConnectionState.CONNECTING)
            try {
                val request = Request.Builder().url(serverUrl).build()
                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        isConnecting = false
                        protobufMode = false // fresh connection, fresh dialect
                        _connectionState.tryEmit(ConnectionState.CONNECTED)
                        Log.d("NebulaListen", "Connected!")
                        // Flush queued messages now that we're connected
                        val queued = messageQueue.toList()
                        messageQueue.clear()
                        queued.forEach { bytes ->
                            Log.d("NebulaListen", "Flushing ${bytes.size} queued bytes")
                            webSocket?.send(ByteString.of(*bytes))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d("NebulaListen", "Received: ${text.take(200)}")
                        _messages.tryEmit(text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val data = bytes.toByteArray()
                        if (data.isEmpty()) return
                        if (data[0] == '{'.code.toByte()) {
                            val text = data.decodeToString()
                            Log.d("NebulaListen", "Received: ${text.take(200)}")
                            _messages.tryEmit(text)
                        } else {
                            // Server chose the Protobuf dialect — mirror it from now on.
                            protobufMode = true
                            Log.d(
                                "NebulaListen",
                                "Server upgraded the dialect to Protobuf — switching protocol mode"
                            )
                            val frame = ProtoWire.toFrame(data)
                            if (frame != null) {
                                Log.d("NebulaListen", "Received (proto): ${frame.take(200)}")
                                _messages.tryEmit(frame)
                            } else {
                                Log.e("NebulaListen", "Undecodable Protobuf frame (${data.size} bytes)")
                                _messages.tryEmit("<binary frame>")
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("NebulaListen", "Connection closing: $code $reason")
                        webSocket?.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("NebulaListen", "Connection closed: $code $reason")
                        this@ListenClient.webSocket = null
                        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                        scheduleReconnect()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        Log.e("NebulaListen", "Connection failed: ${t.message}", t)
                        this@ListenClient.webSocket = null
                        isConnecting = false
                        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                        scheduleReconnect()
                    }
                })
            } catch (e: Exception) {
                Log.e("NebulaListen", "Connection error: ${e.message}", e)
                isConnecting = false
                _connectionState.emit(ConnectionState.DISCONNECTED)
                scheduleReconnect()
            }
        }
    }

    /**
     * Send a message picking the wire dialect the server currently wants:
     * `json` produces the JSON frame, `proto` the Protobuf envelope.
     * Queued until connected.
     */
    fun sendFrame(type: String, json: () -> String, proto: () -> ByteArray) {
        scope.launch {
            val bytes = if (protobufMode) proto() else json().toByteArray(Charsets.UTF_8)
            try {
                val ws = webSocket
                if (ws != null) {
                    Log.d("NebulaListen", "Sending $type (${bytes.size} bytes, ${if (protobufMode) "Protobuf" else "JSON"})")
                    ws.send(ByteString.of(*bytes))
                } else {
                    messageQueue.add(bytes)
                }
            } catch (e: Exception) {
                Log.e("NebulaListen", "Send failed: ${e.message}")
                messageQueue.add(bytes)
            }
        }
    }

    /** Disconnect and cancel reconnection. */
    fun disconnect() {
        reconnectJob?.cancel()
        scope.launch {
            webSocket?.close(1000, "User disconnect")
            webSocket = null
        }
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
    }

    private var connectionErrors: Int = 0

    private fun scheduleReconnect() {
        connectionErrors++
        if (connectionErrors >= 3) return // server is rejecting us; stop hammering
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val delayMs = 2000L
            delay(delayMs)
            connect()
        }
    }
}
