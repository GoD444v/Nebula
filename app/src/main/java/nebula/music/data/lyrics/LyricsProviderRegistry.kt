package nebula.music.data.lyrics

import android.content.Context
import android.content.SharedPreferences

/**
 * Which lyrics providers run, and in what order.
 *
 * Echo-Music's LyricsProviderRegistry concept, original code.
 * The order only breaks ties inside a tier (see LyricsRepository's
 * ranking — synced+words beats synced beats plain, regardless of
 * order); an off flag takes a provider out of the race entirely,
 * which matters when one endpoint is rate-limiting or slow.
 *
 * Persisted in the shared "nebula" preferences file (the
 * LyricsStyleStore idiom). The Application context is injected
 * once from MainActivity (the SearchHistory idiom) because
 * LyricsRepository holds no Context of its own.
 */
object LyricsProviderRegistry {

    data class ProviderInfo(
        /** Stable id — the persisted key and the logcat name. */
        val id: String,
        /** Human label for the Settings provider list. */
        val label: String,
        val enabledByDefault: Boolean = true,
    )

    /** Priority order: the first entry wins ties inside a tier. */
    val all = listOf(
        ProviderInfo("youtubemusic", "YouTube Music"),
        ProviderInfo("unison", "Unison"),
        ProviderInfo("kpoe", "LyricsPlus (Kpoe)"),
        ProviderInfo("kugou", "KuGou"),
        ProviderInfo("lrclib", "LRCLIB"),
    )

    private const val FILE = "nebula"
    private const val KEY_ORDER = "lyrics_provider_order"
    private const val KEY_ENABLED_PREFIX = "lyrics_provider_enabled_"

    private var prefs: SharedPreferences? = null

    /** Called once from MainActivity, beside the other store inits. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /**
     * Enabled providers in priority order. Unknown ids (left over
     * from an older app version) are dropped; ids new to this
     * version append at the end. Before MainActivity runs (unit
     * tests), every enabled-by-default provider runs in [all]
     * order — the same list the fan-out used before the registry
     * existed.
     */
    fun orderedEnabled(): List<ProviderInfo> {
        val stored = prefs
        if (stored == null) return all.filter { it.enabledByDefault }
        val storedOrder = stored.getString(KEY_ORDER, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val byId = all.associateBy { it.id }
        val ordered = storedOrder.mapNotNull { byId[it] } +
            all.filter { it.id !in storedOrder }
        return ordered.filter { isEnabled(stored, it.id) }
    }

    /** All providers in persisted priority order (enabled and disabled). */
    fun orderedAll(): List<ProviderInfo> {
        val stored = prefs
        if (stored == null) return all
        val storedOrder = stored.getString(KEY_ORDER, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val byId = all.associateBy { it.id }
        return storedOrder.mapNotNull { byId[it] } + all.filter { it.id !in storedOrder }
    }

    fun isEnabled(id: String): Boolean {
        val stored = prefs ?: return true
        return isEnabled(stored, id)
    }

    private fun isEnabled(stored: SharedPreferences, id: String): Boolean =
        stored.getBoolean(
            KEY_ENABLED_PREFIX + id,
            all.firstOrNull { it.id == id }?.enabledByDefault ?: true
        )

    fun setEnabled(id: String, enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED_PREFIX + id, enabled)?.apply()
    }

    /** Persists a new priority order. Ids not in [all] are dropped on read. */
    fun setOrder(ids: List<String>) {
        prefs?.edit()?.putString(KEY_ORDER, ids.joinToString(","))?.apply()
    }
}
