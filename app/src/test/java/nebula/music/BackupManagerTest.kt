package nebula.music

import nebula.music.data.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManagerTest {

    private fun sample() = BackupManager.Backup(
        listOf(
            BackupManager.BackupPlaylist(
                "Tamil hits",
                listOf(
                    BackupManager.BackupSong("v1", "Hey Minnale", "Haricharan", "http://t/1", 1000L),
                    BackupManager.BackupSong("v2", "Monica \"Coolie\"", "Subla,shini", "", 2000L)
                )
            )
        ),
        BackupManager.BackupSettings(
            genres = "Tamil|b|p",
            artists = "A.R. Rahman|x,y",
            themeMode = "dark",
            paletteId = "custom",
            customColors = listOf(1, 2, 3, 4, 5, 6, 7),
            tabOrder = "home,search",
            homeShow = false,
            homeVisibility = "5=0",
            wifiOnly = true
        )
    )

    @Test
    fun roundTripPreservesEverything() {
        val out = BackupManager.import(BackupManager.export(sample().playlists, sample().settings))
        assertEquals(sample(), out)
    }

    @Test
    fun malformedReturnsNull() {
        assertNull(BackupManager.import("not json"))
        assertNull(BackupManager.import("""{"version":999,"playlists":[]}"""))
        assertNull(BackupManager.import("""{"version":1,"playlists":[{"songs":[]}]}"""))
    }
}
