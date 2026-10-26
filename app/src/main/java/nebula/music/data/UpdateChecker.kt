package nebula.music.data

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The outcome of an update check. */
sealed class UpdateResult {
    data class UpToDate(val currentVersion: String) : UpdateResult()
    data class UpdateAvailable(val latestVersion: String, val apkUrl: String, val fileSize: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

/**
 * Checks the GitHub releases of GoD444v/Nebula for a newer build.
 *
 * The latest release's tag is compared against the installed
 * versionName via [isNewerVersion]. No key, no account — the
 * releases API is public.
 */
object UpdateChecker {
    private const val RELEASES_LATEST =
        "https://api.github.com/repos/GoD444v/Nebula/releases/latest"

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun check(context: Context): UpdateResult {
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        return try {
            val root = Json.parseToJsonElement(http.get(RELEASES_LATEST).bodyAsText()).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (tag.isBlank()) return UpdateResult.Error("No release published yet")
            val asset = (root["assets"] as? JsonArray)?.firstOrNull { el ->
                val name = (el as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                name.endsWith(".apk", true) || name.endsWith(".zip", true)
            } as? JsonObject
            if (isNewerVersion(tag, current)) {
                UpdateResult.UpdateAvailable(
                    latestVersion = tag,
                    apkUrl = asset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    fileSize = asset?.get("size")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?.let { "%.1f MB".format(it / (1024.0 * 1024.0)) } ?: ""
                )
            } else {
                UpdateResult.UpToDate(current)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            UpdateResult.Error(e.message ?: "Update check failed")
        }
    }
}
