package nebula.music.data.listen

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Hand-rolled Protobuf wire codec (zero dependencies).
 *
 * The server speaks Protobuf envelopes: field 1 = type string, field 2 =
 * payload bytes (a NESTED protobuf message), field 3 = compressed bool.
 * JSON is the client's opening dialect; once the server replies with a binary
 * frame the client upgrades all sends to Protobuf (gzip when worthwhile).
 *
 * Incoming Protobuf frames are converted to the SAME JSON shape the JSON
 * dialect uses, so every reader downstream (payloadOf, screen, sync) works
 * unchanged against both dialects.
 */
object ProtoWire {

    // --- Envelope decode ---

    /** Decode one envelope into a ListenMessage JSON string. Null if not decodable. */
    fun toFrame(data: ByteArray): String? {
        val env = runCatching { decodeEnvelope(data) }.getOrNull() ?: return null
        val (type, payloadBytes, compressed) = env
        val inner = if (compressed) gunzip(payloadBytes) ?: payloadBytes else payloadBytes
        val payloadJson: JsonObject? = toJsonPayload(type, inner)
        return listenJson.encodeToString(
            ListenMessage.serializer(),
            ListenMessage(type, payloadJson)
        )
    }

    private fun decodeEnvelope(data: ByteArray): Triple<String, ByteArray, Boolean> {
        var type: String? = null
        var payload: ByteArray? = null
        var compressed = false
        var i = 0
        while (i < data.size) {
            val (tag, next) = readVarint(data, i)
            i = next
            when ((tag and 0x7).toInt()) {
                0 -> {
                    val (v, j) = readVarint(data, i)
                    if (((tag shr 3) and 0x1F).toInt() == 3 && v == 1L) compressed = true
                    i = j
                }
                1 -> i += 8 // fixed64 — not expected, skip
                2 -> {
                    val (len, j) = readVarint(data, i)
                    i = j
                    val size = len.toInt()
                    val chunk = data.copyOfRange(i, i + size)
                    i += size
                    when (((tag shr 3) and 0x1F).toInt()) {
                        1 -> type = String(chunk, Charsets.UTF_8)
                        2 -> payload = chunk
                    }
                }
                5 -> i += 4 // fixed32 — not expected at envelope level, skip
                else -> throw IllegalArgumentException("bad wire type")
            }
        }
        return Triple(type ?: "", payload ?: ByteArray(0), compressed)
    }

    /** Nested payload message -> JsonObject using the JSON dialect's keys. */
    private fun toJsonPayload(type: String, inner: ByteArray): JsonObject? {
        if (inner.isEmpty()) return null
        val f = runCatching { readFields(inner) }.getOrNull() ?: return null
        return when (type) {
            MessageTypes.ROOM_CREATED -> buildJsonObject {
                put("room_code", f.str(1))
                put("user_id", f.str(2))
                put("session_token", f.str(3))
            }
            MessageTypes.JOIN_REQUEST -> buildJsonObject {
                put("user_id", f.str(1))
                put("username", f.str(2))
            }
            MessageTypes.JOIN_APPROVED -> buildJsonObject {
                put("room_code", f.str(1))
                put("user_id", f.str(2))
                put("session_token", f.str(3))
                put("state", f.msg(4)?.let { roomStateJson(it) } ?: JsonNull)
            }
            MessageTypes.JOIN_REJECTED -> buildJsonObject { put("reason", f.str(1)) }
            MessageTypes.ERROR -> buildJsonObject {
                put("code", f.str(1))
                put("message", f.str(2))
            }
            MessageTypes.USER_JOINED, MessageTypes.USER_LEFT -> buildJsonObject {
                put("user_id", f.str(1))
                put("username", f.str(2))
            }
            MessageTypes.HOST_CHANGED -> buildJsonObject {
                put("new_host_id", f.str(1))
                put("new_host_name", f.str(2))
            }
            MessageTypes.KICKED -> buildJsonObject { put("reason", f.str(1)) }
            MessageTypes.SYNC_PLAYBACK -> buildJsonObject {
                put("action", f.str(1))
                put("track_id", f.str(2))
                f.varintNum(3)?.let { put("position", it) }
                f.msg(4)?.let { put("track_info", trackJson(it)) }
                f.msgList(6)?.let { put("queue", JsonArray(it.map { t -> trackJson(t) })) }
                f.fixed32(8)?.let { put("volume", it) }
                f.varintNum(9)?.let { put("server_time", it) }
            }
            MessageTypes.SYNC_STATE -> buildJsonObject {
                f.msg(1)?.let { put("current_track", trackJson(it)) }
                f.varintNum(2)?.let { put("is_playing", it != 0L) }
                f.varintNum(3)?.let { put("position", it) }
                f.varintNum(4)?.let { put("last_update", it) }
                f.msgList(5)?.let { put("queue", JsonArray(it.map { t -> trackJson(t) })) }
                f.fixed32(6)?.let { put("volume", it) }
            }
            else -> null // pong & unknown types carry nothing we need
        }
    }

    private fun trackJson(t: ProtoFields): JsonObject = buildJsonObject {
        put("id", t.str(1))
        put("title", t.str(2))
        put("artist", t.str(3))
        put("album", t.str(4))
        t.varintNum(5)?.let { put("duration", it) }
        put("thumbnail", t.str(6))
        put("suggested_by", t.str(7))
    }

    private fun roomStateJson(r: ProtoFields): JsonObject = buildJsonObject {
        put("room_code", r.str(1))
        put("host_id", r.str(2))
        put("users", JsonArray(r.msgList(3).orEmpty().map { userJson(it) }))
        r.msg(4)?.let { put("current_track", trackJson(it)) }
        r.varintNum(5)?.let { put("is_playing", it != 0L) }
        r.varintNum(6)?.let { put("position", it) }
        r.varintNum(7)?.let { put("last_update", it) }
        r.fixed32(8)?.let { put("volume", it) }
        put("queue", JsonArray(r.msgList(9).orEmpty().map { trackJson(it) }))
        r.varintNum(10)?.let { put("allow_participant_control", it != 0L) }
    }

    private fun userJson(u: ProtoFields): JsonObject = buildJsonObject {
        put("user_id", u.str(1))
        put("username", u.str(2))
        u.varintNum(3)?.let { put("is_host", it != 0L) }
        u.varintNum(4)?.let { put("is_connected", it != 0L) }
    }

    // --- Encode: envelope + the payloads we send ---

    private fun envelope(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, 1, type)
        if (payload.isNotEmpty()) writeBytes(out, 2, payload)
        return out.toByteArray()
    }

    private fun gzip(data: ByteArray): ByteArray {
        val sink = ByteArrayOutputStream()
        GZIPOutputStream(sink).use { it.write(data) }
        return sink.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray? = runCatching {
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }.getOrNull()

    /** Send-side envelope: gzip-compress when it actually helps. */
    private fun envelopeAuto(type: String, payload: ByteArray): ByteArray {
        if (payload.size > 100) {
            val packed = gzip(payload)
            if (packed.size < payload.size) {
                val out = ByteArrayOutputStream()
                writeString(out, 1, type)
                writeBytes(out, 2, packed)
                writeVarint(out, tag(3, 0))
                out.write(1)
                return out.toByteArray()
            }
        }
        return envelope(type, payload)
    }

    fun encodeCreateRoom(username: String): ByteArray =
        envelopeAuto(MessageTypes.CREATE_ROOM, msg { writeString(it, 1, username) })

    fun encodeJoinRoom(roomCode: String, username: String): ByteArray =
        envelopeAuto(MessageTypes.JOIN_ROOM, msg { out ->
            writeString(out, 1, roomCode)
            writeString(out, 2, username)
        })

    fun encodeApproveJoin(userId: String): ByteArray =
        envelopeAuto(MessageTypes.APPROVE_JOIN, msg { out -> writeString(out, 1, userId) })

    fun encodeRejectJoin(userId: String, reason: String): ByteArray = envelopeAuto(
        MessageTypes.REJECT_JOIN,
        msg { out ->
            writeString(out, 1, userId)
            if (reason.isNotEmpty()) writeString(out, 2, reason)
        }
    )

    fun encodePing(): ByteArray = envelopeAuto(MessageTypes.PING, ByteArray(0))

    fun encodePlaybackAction(
        action: String,
        trackId: String?,
        positionMs: Long?,
        trackInfo: TrackInfo?,
        queue: List<TrackInfo>?,
        volume: Float?,
        serverTime: Long?
    ): ByteArray = envelopeAuto(MessageTypes.PLAYBACK_ACTION, msg { out ->
        writeString(out, 1, action)
        trackId?.let { writeString(out, 2, it) }
        positionMs?.let { writeVarint(out, tag(3, 0)); writeVarint(out, it) }
        trackInfo?.let { writeBytes(out, 4, trackBytes(it)) }
        queue?.forEach { writeBytes(out, 6, trackBytes(it)) }
        volume?.let { writeVarint(out, tag(8, 5)); writeFixed32(out, it) }
        serverTime?.let { writeVarint(out, tag(9, 0)); writeVarint(out, it) }
    })

    private fun trackBytes(t: TrackInfo): ByteArray = msg { out ->
        writeString(out, 1, t.id)
        writeString(out, 2, t.title)
        writeString(out, 3, t.artist)
        t.album?.takeIf { it.isNotEmpty() }?.let { writeString(out, 4, it) }
        if (t.durationMs > 0) {
            writeVarint(out, tag(5, 0))
            writeVarint(out, t.durationMs)
        }
        t.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let { writeString(out, 6, it) }
        t.suggestedBy?.takeIf { it.isNotEmpty() }?.let { writeString(out, 7, it) }
    }

    // --- Protobuf primitives ---

    private fun msg(block: (ByteArrayOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        block(out)
        return out.toByteArray()
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7F.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun tag(fieldNumber: Int, wireType: Int): Long =
        ((fieldNumber and 0x1F).toLong() shl 3) or wireType.toLong()

    private fun writeString(out: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, tag(fieldNumber, 2))
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeBytes(out: ByteArrayOutputStream, fieldNumber: Int, value: ByteArray) {
        writeVarint(out, tag(fieldNumber, 2))
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    private fun writeFixed32(out: ByteArrayOutputStream, value: Float) {
        val bits = java.lang.Float.floatToIntBits(value)
        out.write(bits and 0xFF)
        out.write((bits shr 8) and 0xFF)
        out.write((bits shr 16) and 0xFF)
        out.write((bits shr 24) and 0xFF)
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            i++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, i)
    }

    // --- Nested message field reader ---

    /** Flat lookup of a parsed message's fields, lazily scoped per message. */
    private class ProtoFields(fields: List<Pair<Int, ProtoValue>>) {
        private val byField = fields.groupBy { it.first }
        fun str(field: Int): String? =
            byField[field]?.firstNotNullOfOrNull { (it.second as? ProtoValue.Bytes)?.str() }
        fun varintNum(field: Int): Long? =
            byField[field]?.firstNotNullOfOrNull { (it.second as? ProtoValue.Varint)?.v }
        fun fixed32(field: Int): Float? =
            byField[field]?.firstNotNullOfOrNull { (it.second as? ProtoValue.Fixed32)?.f }
        fun msg(field: Int): ProtoFields? =
            byField[field]?.firstNotNullOfOrNull { (it.second as? ProtoValue.Bytes)?.asMessage() }
        fun msgList(field: Int): List<ProtoFields>? =
            byField[field]?.mapNotNull { (it.second as? ProtoValue.Bytes)?.asMessage() }
                ?.takeIf { it.isNotEmpty() }
    }

    private sealed class ProtoValue {
        class Varint(val v: Long) : ProtoValue()
        class Fixed32(val f: Float) : ProtoValue()
        class Bytes(val b: ByteArray) : ProtoValue() {
            fun str(): String? = runCatching { String(b, Charsets.UTF_8) }.getOrNull()
            fun asMessage(): ProtoFields? = runCatching { parseMessageFields(b) }.getOrNull()
        }
    }

    private fun parseMessageFields(data: ByteArray): ProtoFields {
        val fields = mutableListOf<Pair<Int, ProtoValue>>()
        var i = 0
        while (i < data.size) {
            val (tag, next) = readVarint(data, i)
            i = next
            val field = ((tag shr 3) and 0x1F).toInt()
            when ((tag and 0x7).toInt()) {
                0 -> {
                    val (v, j) = readVarint(data, i)
                    fields.add(field to ProtoValue.Varint(v))
                    i = j
                }
                1 -> i += 8
                2 -> {
                    val (len, j) = readVarint(data, i)
                    i = j
                    val size = len.toInt()
                    fields.add(field to ProtoValue.Bytes(data.copyOfRange(i, i + size)))
                    i += size
                }
                5 -> {
                    val bits = (data[i].toLong() and 0xFF) or
                        ((data[i + 1].toLong() and 0xFF) shl 8) or
                        ((data[i + 2].toLong() and 0xFF) shl 16) or
                        ((data[i + 3].toLong() and 0xFF) shl 24)
                    fields.add(field to ProtoValue.Fixed32(java.lang.Float.intBitsToFloat(bits.toInt())))
                    i += 4
                }
                else -> throw IllegalArgumentException("bad wire type")
            }
        }
        return ProtoFields(fields)
    }

    private fun readFields(data: ByteArray): ProtoFields = parseMessageFields(data)
}
