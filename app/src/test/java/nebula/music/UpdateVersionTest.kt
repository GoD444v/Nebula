package nebula.music

import nebula.music.data.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version comparison behind the update status flag. */
class UpdateVersionTest {

    @Test
    fun `higher segment wins`() {
        assertTrue(isNewerVersion("1.1", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.1"))
    }

    @Test
    fun `multi digit segments compare numerically not lexicographically`() {
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertFalse(isNewerVersion("1.9", "1.10"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(isNewerVersion("1.0", "1.0"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertTrue(isNewerVersion("v2.0", "v1.9"))
        assertFalse(isNewerVersion("v1.9", "v1.9"))
    }

    @Test
    fun `missing segment counts as zero`() {
        assertTrue(isNewerVersion("1.0.1", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.0.1"))
    }
}
