package nebula.music

import nebula.music.data.lyrics.Romanizer
import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Latin reading of a Korean line.
 *
 * Every case below is a reading that other romanizers print, so a table typo
 * cannot pass by looking plausible. Worth the test because a wrong reading is
 * silent and shows up under every line of the song at once.
 */
class KoreanRomanizerTest {

    private fun romanize(text: String) = Romanizer.romanize(text)

    @Test
    fun `plain syllables`() {
        assertEquals("saranghaeyo", romanize("사랑해요"))
    }

    @Test
    fun `a coda moves onto a following silent vowel`() {
        // The case the whole table exists for: 국 alone ends in "k", but the ㄱ is
        // written with its initial value once 어 follows — "hangugeo", never
        // "hangukeo".
        assertEquals("hangugeo", romanize("한국어"))
        assertEquals("seouri", romanize("서울이"))
        // ㅎ borrows the same way, as its own "h": 좋아 is "joha" as written.
        assertEquals("joha", romanize("좋아"))
    }

    @Test
    fun `a coda stays its own letter before a plain consonant`() {
        assertEquals("hakgyo", romanize("학교"))
    }

    @Test
    fun `a coda turns nasal before n or m`() {
        // Revised Romanization follows the sound rather than the spelling: the ㅂ of
        // 합니다 is written "m" because that is what it says. "gamsahapnida" is the
        // spelling, and it reads as a typo to anyone who has seen the word written.
        assertEquals("gamsahamnida", romanize("감사합니다"))
        assertEquals("itseumnida", romanize("있습니다"))
        assertEquals("hangungmal", romanize("한국말"))
        // ㅎ is silenced by ㄴ/ㅁ rather than nasalized.
        assertEquals("jone", romanize("좋네"))
    }

    @Test
    fun `closed syllables`() {
        assertEquals("annyeonghaseyo", romanize("안녕하세요"))
    }

    @Test
    fun `coda clusters keep their letters when a vowel follows`() {
        // ㅄ, ㄵ and ㄶ are the clusters a lyric actually meets. Nothing moves, and
        // the last letter already reads as the next syllable's sound.
        assertEquals("eopseoyo", romanize("없어요"))
        assertEquals("anja", romanize("앉아"))
        assertEquals("manha", romanize("많아"))
    }

    @Test
    fun `a coda cluster nasalizes before n or m like a single coda`() {
        // Found by running 179 real lyric lines through this: 없는 was coming out
        // "eopsneun" where every romanizer prints "eomneun", because the cluster was
        // missing from the nasal table that already handled the plain ㅂ of 합니다.
        assertEquals("eomneun", romanize("없는"))
        assertEquals("anne", romanize("앉네"))
        assertEquals("samneun", romanize("삶는"))
    }

    @Test
    fun `a liquid coda shares an ll with a following n or l`() {
        // The one place the letter tables alone give a sound Korean does not have:
        // 빨리 reads "ppalri" and 달려 "dalryeo" without this, and "lr" is not a
        // sequence a Korean speaker makes, so it reads as a glitch. 8 of the 149
        // Korean lines in the sample hit it — 빨리, 달려, 머물러, 끌려, 이끌림.
        assertEquals("ppalli", romanize("빨리"))
        assertEquals("dallyeoga", romanize("달려가"))
        assertEquals("meomulleo", romanize("머물러"))
        // ㄴ and ㄹ in either order take the same "ll": 신라 is "silla", 설날 "seollal".
        assertEquals("silla", romanize("신라"))
        assertEquals("seollal", romanize("설날"))
        // ㄴ + ㄴ is left alone, because two ㄴs stay two letters: 언니 is "eonni".
        assertEquals("eonni", romanize("언니"))
    }

    @Test
    fun `everything that is not hangul passes through`() {
        assertEquals("Good sarang", romanize("Good 사랑"))
        assertEquals("", romanize(""))
        assertEquals("Yeah, oh!", romanize("Yeah, oh!"))
        // A stray kanji stays itself rather than being dropped or guessed at.
        assertEquals("sarang 恋", romanize("사랑 恋"))
    }

    @Test
    fun `only korean lines are given a reading`() {
        val filled = Romanizer.fill(
            LyricsResponse(listOf(LyricLine(0L, "사랑해요"), LyricLine(1L, "I love you")))
        )
        assertEquals("saranghaeyo", filled.lines[0].romanized)
        assertEquals("", filled.lines[1].romanized)
    }

    @Test
    fun `a song with no hangul comes back as the same instance`() {
        // The caller compares instances to decide whether to re-emit the list, so a
        // needless copy would cost a full rebuild of the lyrics on every English song.
        val response = LyricsResponse(listOf(LyricLine(0L, "I love you")))
        assertSame(response, Romanizer.fill(response))
    }
}
