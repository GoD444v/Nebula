package nebula.music

import nebula.music.data.lyrics.LyricsTranslator
import nebula.music.data.models.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The script sniff behind the English lyric line.
 *
 * Worth a test because every failure mode here is silent: a song the sniff gets
 * wrong just never grows a translation, which looks exactly like "this song has
 * no translation". The codes asserted below are ML Kit's BCP-47 language codes.
 */
class LyricsLanguageTest {

    private fun lines(vararg text: String) = text.map { LyricLine(0L, it) }

    @Test
    fun `kana means japanese`() {
        assertEquals("ja", LyricsTranslator.sourceLanguage(lines("無敵の笑顔で荒らすメディア")))
    }

    @Test
    fun `an english hook does not change a japanese song`() {
        assertEquals(
            "ja",
            LyricsTranslator.sourceLanguage(lines("Hello", "君の名前を呼ぶよ", "Never let you go"))
        )
    }

    @Test
    fun `hangul means korean`() {
        assertEquals("ko", LyricsTranslator.sourceLanguage(lines("아무리 막아도")))
    }

    @Test
    fun `kanji without kana falls back to chinese`() {
        assertEquals("zh", LyricsTranslator.sourceLanguage(lines("我的天空")))
    }

    @Test
    fun `cyrillic means russian`() {
        assertEquals("ru", LyricsTranslator.sourceLanguage(lines("Привет мир")))
    }

    @Test
    fun `english lyrics have nothing to translate`() {
        assertNull(LyricsTranslator.sourceLanguage(lines("Hello from the other side")))
    }

    @Test
    fun `blank lines do not count as a language`() {
        assertNull(LyricsTranslator.sourceLanguage(lines("", "   ", "English only")))
    }

    @Test
    fun `an english line inside a japanese song is left alone`() {
        assertFalse(LyricsTranslator.hasTranslatableScript("Never let you go"))
        assertTrue(LyricsTranslator.hasTranslatableScript("君の名前を呼ぶよ"))
    }

}
