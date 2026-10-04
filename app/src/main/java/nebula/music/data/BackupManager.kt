package nebula.music.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Full-app backup: playlists + songs + every personalization setting, one JSON
 * file through the system picker. No accounts, no servers.
 *
 * Pure kotlinx.serialization.json DOM — no compiler plugin, no Android APIs —
 * so export/import round-trips in a plain JVM unit test.
 *
 * Excluded deliberately: the Downloaded system mirror (rebuilds from the
 * download index), on-device songs (paths don't restore), caches.
 */
object BackupManager {
    const val VERSION = 1

    data class BackupSong(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumb: String,
        val addedAt: Long
    )

    data class BackupPlaylist(
        val name: String,
        val songs: List<BackupSong>
    )

    /** Custom palette as 7 ARGB ints in VoxCustom constructor order. */
    data class BackupSettings(
        val genres: String = "",
        val artists: String = "",
        val themeMode: String = "system",
        val paletteId: String = "classic",
        val customColors: List<Int> = emptyList(),
        val tabOrder: String = "",
        val homeShow: Boolean = true,
        val homeVisibility: String = "",
        val wifiOnly: Boolean = false
    )

    data class Backup(
        val playlists: List<BackupPlaylist>,
        val settings: BackupSettings
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun export(playlists: List<BackupPlaylist>, settings: BackupSettings): String =
        buildJsonObject {
            put("version", JsonPrimitive(VERSION))
            put("playlists", buildJsonArray {
                for (p in playlists) {
                    add(buildJsonObject {
                        put("name", JsonPrimitive(p.name))
                        put("songs", buildJsonArray {
                            for (s in p.songs) {
                                add(buildJsonObject {
                                    put("v", JsonPrimitive(s.videoId))
                                    put("t", JsonPrimitive(s.title))
                                    put("a", JsonPrimitive(s.artist))
                                    put("u", JsonPrimitive(s.thumb))
                                    put("at", JsonPrimitive(s.addedAt))
                                })
                            }
                        })
                    })
                }
            })
            put("settings", buildJsonObject {
                put("genres", JsonPrimitive(settings.genres))
                put("artists", JsonPrimitive(settings.artists))
                put("themeMode", JsonPrimitive(settings.themeMode))
                put("paletteId", JsonPrimitive(settings.paletteId))
                put("customColors", buildJsonArray {
                    for (c in settings.customColors) add(JsonPrimitive(c))
                })
                put("tabOrder", JsonPrimitive(settings.tabOrder))
                put("homeShow", JsonPrimitive(settings.homeShow))
                put("homeVisibility", JsonPrimitive(settings.homeVisibility))
                put("wifiOnly", JsonPrimitive(settings.wifiOnly))
            })
        }.toString()

    /** Null on malformed input — callers toast, never half-restore. */
    fun import(text: String): Backup? = try {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["version"]?.jsonPrimitive?.intOrNull != VERSION) return null
        val playlists = root["playlists"]?.jsonArray.orEmpty().map { entry ->
            val o = entry.jsonObject
            BackupPlaylist(
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null,
                songs = o["songs"]?.jsonArray.orEmpty().map { s ->
                    val so = s.jsonObject
                    BackupSong(
                        videoId = so["v"]?.jsonPrimitive?.contentOrNull ?: return null,
                        title = so["t"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        artist = so["a"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        thumb = so["u"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        addedAt = so["at"]?.jsonPrimitive?.longOrNull ?: 0L
                    )
                }
            )
        }
        val s = root["settings"]?.jsonObject ?: JsonObject(emptyMap())
        fun str(key: String) = s[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        Backup(
            playlists,
            BackupSettings(
                genres = str("genres"),
                artists = str("artists"),
                themeMode = str("themeMode").ifBlank { "system" },
                paletteId = str("paletteId").ifBlank { "classic" },
                customColors = s["customColors"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty(),
                tabOrder = str("tabOrder"),
                homeShow = s["homeShow"]?.jsonPrimitive?.booleanOrNull ?: true,
                homeVisibility = str("homeVisibility"),
                wifiOnly = s["wifiOnly"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        )
    } catch (_: Exception) {
        null
    }
}
