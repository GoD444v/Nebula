package nebula.music.data.lyrics

import nebula.music.data.models.LyricLine
import nebula.music.data.models.LyricsResponse

/**
 * Hangul -> Latin reading, so a line can be followed by someone who cannot read
 * hangul at all.
 *
 * Korean only, and the narrow scope is the point. Japanese was the original ask
 * and it cannot be answered without a kanji dictionary: kana-only romanization
 * turns 君の名前を呼ぶよ into 君no名前wo呼buよ, which is worse than leaving it
 * alone, and no payload carries a reading to use instead — Unison's TTML for two
 * Japanese songs (24KB and 44KB) has no `<rt>` element and no ruby attribute, and
 * LyricsPlus lines carry exactly time/duration/text/syllabus. Hangul has neither
 * problem: it is alphabetic, so the reading is arithmetic, offline, needs no
 * dictionary and adds no dependency.
 *
 * ponytail: four sound rules are implemented and the rest are not. In: a coda moves
 * onto a following ㅇ (한국어 -> "hangugeo", never "hangukeo"); a plain coda turns
 * nasal before ㄴ or ㅁ (감사합니다 -> "gamsahamnida", not "gamsahapnida"); a coda
 * cluster does the same (없는 -> "eomneun"); and a liquid coda shares an "ll" with a
 * following ㄴ or ㄹ (빨리 -> "ppalli", 신라 -> "silla", 설날 -> "seollal"). Revised
 * Romanization follows the sound, and the spelled-out form of any of those reads as a
 * typo to someone who has seen the word written. Out: palatalization, ㅎ aspiration and
 * vowel contraction, so ㅎ keeps its letter wherever it is not silenced — 좋아 is
 * "joha" and 않아 "anha", not the spoken "joa" and "ana". That is the one place this
 * reads as spelling rather than sound, and it is the cheapest rule to add back if a
 * lyric ever looks wrong because of it.
 */
object Romanizer {

    private const val FIRST_SYLLABLE = 0xAC00
    private const val LAST_SYLLABLE = 0xD7A3
    private const val SYLLABLE_COUNT = LAST_SYLLABLE - FIRST_SYLLABLE + 1

    private const val VOWEL_COUNT = 21
    private const val CODA_COUNT = 28
    private const val SYLLABLES_PER_INITIAL = VOWEL_COUNT * CODA_COUNT

    /** ㅇ: the carrier that holds a syllable open and has no sound of its own. */
    private const val SILENT_INITIAL = 11

    /** ㄴ, ㄹ and ㅁ: the initials that change what a coda before them is written as. */
    private const val INITIAL_N = 2
    private const val INITIAL_L = 5
    private const val INITIAL_M = 6

    /** The two codas that share an "ll" with a following ㄴ or ㄹ. */
    private const val CODA_N = 4
    private const val CODA_L = 8

    /** 19 initial consonants. */
    private val INITIAL = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )

    /** 21 vowels. */
    private val VOWEL = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )

    /** 28 final consonants, as written when nothing follows them. */
    private val CODA = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk",
        "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t",
        "t", "ng", "t", "t", "k", "t", "p", "t"
    )

    /**
     * Which INITIAL a coda becomes when the next syllable borrows it, or -1 when it
     * stays put.
     *
     * A coda is written one way alone and another when a vowel follows, and that
     * difference is the whole reason this table exists: 국 alone ends in "k"
     * ("guk"), but the moment 어 follows, the ㄱ is written with its initial value
     * and 한국어 comes out "hangugeo" rather than "hangukeo".
     *
     * Clusters (ㄳ ㄵ ㄶ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅄ) are -1: they already carry their
     * last letter into the next syllable's letters without anything moving, which is
     * what makes 앉아 "anja". ㅇ is -1 because it is only ever a carrier.
     */
    private val MOVES_TO_INITIAL = intArrayOf(
        -1, 0, 1, -1, 2, -1, -1, 3, 5, -1,
        -1, -1, -1, -1, -1, -1, 6, 7, -1, 9,
        10, -1, 12, 14, 15, 16, 17, 18
    )

    /**
     * How a coda is written when ㄴ or ㅁ follows it, or null when nothing changes.
     *
     * The nine codas that make a plain stop — ㄱ ㄲ ㄷ ㅂ ㅅ ㅆ ㅈ ㅊ ㅋ ㅌ ㅍ — turn into
     * a nose against ㄴ/ㅁ, and the nasal is chosen by where the stop was made: ㄱ and
     * ㅋ go to ㅇ, ㄷ ㅅ ㅆ ㅈ ㅊ ㅌ go to ㄴ, ㅂ ㅍ go to ㅁ. That is why 감사합니다 is
     * "gamsahamnida" and not "gamsahapnida".
     *
     * The four clusters a lyric actually meets take it too, because their last letter
     * is one of those same stops — ㅄ is an ㅂ and ㄻ is an ㅁ (없는 -> "eomneun", 삶는
     * -> "samneun"), while ㄵ and ㄶ both land on ㄴ (앉네 -> "anne"). ㅎ is silenced by
     * ㄴ/ㅁ instead of nasalized (좋네 -> "jone"), and the codas that are already nasal
     * or have no sound of their own need no entry.
     */
    private val BEFORE_NASAL = arrayOf(
        null, "ng", "ng", null, null, "n", "n", "n", null, null,
        "m", null, null, null, null, null, null, "m", "m", "n",
        "n", null, "n", "n", "ng", "n", "m", ""
    )

    /**
     * The same response with [LyricLine.romanized] filled in, or [response] itself
     * when no line needs it. Callers compare instances to skip a needless re-emit,
     * the same contract [LyricsTranslator.translate] keeps.
     */
    fun fill(response: LyricsResponse): LyricsResponse {
        val lines = response.lines
        if (lines.isEmpty()) return response
        var changed = false
        val out = lines.map { line ->
            if (!hasHangul(line.text)) return@map line
            changed = true
            line.copy(romanized = romanize(line.text))
        }
        return if (changed) response.copy(lines = out) else response
    }

    /**
     * The line as Latin letters, with everything that is not hangul passed through
     * untouched — Latin words, spaces, punctuation and any Japanese or Chinese
     * characters a Korean lyric happens to mix in.
     *
     * Internal rather than private so RomanizerTest can hold it to known readings:
     * a wrong table entry here is silent, and a silent wrong reading is printed
     * under every line of the song.
     */
    internal fun romanize(text: String): String {
        val out = StringBuilder(text.length * 2)
        // Set when the letter just written was the first half of a shared "ll", so the
        // next syllable's initial brings nothing of its own to it.
        var sharedLiquid = false
        for ((i, ch) in text.withIndex()) {
            val syllable = ch.code - FIRST_SYLLABLE
            if (syllable !in 0 until SYLLABLE_COUNT) {
                out.append(ch)
                sharedLiquid = false
                continue
            }
            out.append(if (sharedLiquid) "l" else INITIAL[syllable / SYLLABLES_PER_INITIAL])
            sharedLiquid = false
            out.append(VOWEL[syllable % SYLLABLES_PER_INITIAL / CODA_COUNT])

            val coda = syllable % CODA_COUNT
            if (coda == 0) continue

            val borrowed = MOVES_TO_INITIAL[coda]
            val next = initialAt(text, i + 1)
            out.append(
                when {
                    // Before both tables: a liquid pair is one shared "ll", which is not
                    // something the letter tables can express on their own.
                    sharesLiquid(coda, next) -> {
                        sharedLiquid = true
                        "l"
                    }
                    borrowed >= 0 && next == SILENT_INITIAL -> INITIAL[borrowed]
                    next == INITIAL_N || next == INITIAL_M -> BEFORE_NASAL[coda] ?: CODA[coda]
                    else -> CODA[coda]
                }
            )
        }
        return out.toString()
    }

    /**
     * True when a liquid coda meets a ㄴ or ㄹ initial, in any combination but ㄴ + ㄴ.
     *
     * Without this the two letters are written separately and the line reads "dalryeo"
     * for 달려 and "ppalri" for 빨리 — and "lr" is not a sequence Korean speakers make
     * at all, so it reads as a glitch rather than as an accent. Revised Romanization
     * writes every one of these pairs as "ll": 신라 is "silla" and 설날 "seollal". ㄴ +
     * ㄴ is left out deliberately, because two ㄴs stay two letters there (언니 is
     * "eonni"), and the letters already produce that.
     */
    private fun sharesLiquid(coda: Int, next: Int?): Boolean = when (coda) {
        CODA_L -> next == INITIAL_L || next == INITIAL_N
        CODA_N -> next == INITIAL_L
        else -> false
    }

    /** The initial of the syllable at [index], or null when there is no syllable there. */
    private fun initialAt(text: String, index: Int): Int? {
        val ch = text.getOrNull(index) ?: return null
        val syllable = ch.code - FIRST_SYLLABLE
        if (syllable !in 0 until SYLLABLE_COUNT) return null
        return syllable / SYLLABLES_PER_INITIAL
    }

    private fun hasHangul(text: String): Boolean =
        text.any { it.code - FIRST_SYLLABLE in 0 until SYLLABLE_COUNT }
}
