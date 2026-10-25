package com.meeting.copilot

import com.meeting.copilot.caption.CaptionAssembler
import com.meeting.copilot.caption.CaptionAssembler.Region
import com.meeting.copilot.caption.align
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionAssemblerTest {

    private var now = 0L
    private val utterances = mutableListOf<Pair<String?, String>>()
    private var live = ""
    private var locked: String? = null

    private val assembler = CaptionAssembler(
        listener = object : CaptionAssembler.Listener {
            override fun onLive(text: String, speaker: String?) {
                live = text
            }

            override fun onUtterance(text: String, speaker: String?, startedAt: Long) {
                utterances.add(speaker to text)
            }

            override fun onSourceLocked(key: String) {
                locked = key
            }
        },
        clock = { now },
    )

    private fun show(text: String, key: String = "zoom|caption", explicit: Boolean = true, speaker: String? = null) {
        now += 300
        assembler.observe(listOf(Region(key, text, speaker, explicit)))
    }

    private fun pause() {
        now += CaptionAssembler.IDLE_COMMIT_MS + 100
        assembler.tick()
    }

    @Test
    fun growingCaptionBecomesOneSentenceAfterPause() {
        show("Bună")
        show("Bună ziua")
        show("Bună ziua tuturor")
        show("Bună ziua tuturor, începem ședința")
        assertTrue(utterances.isEmpty())
        assertEquals("Bună ziua tuturor, începem ședința", live)
        pause()
        assertEquals(listOf("Bună ziua tuturor, începem ședința"), utterances.map { it.second })
    }

    @Test
    fun scrolledAwayWordsAreFinalAndNotDuplicated() {
        show("Avem bugetul aprobat.")
        show("Avem bugetul aprobat. Acum vorbim despre")
        show("aprobat. Acum vorbim despre termene") // first two words scrolled away
        show("Acum vorbim despre termene și livrare")
        pause()
        val all = utterances.joinToString(" ") { it.second }
        assertEquals("Avem bugetul aprobat. Acum vorbim despre termene și livrare", all)
        assertEquals("Avem bugetul aprobat.", utterances.first().second)
    }

    @Test
    fun revisedWordsAreReplacedNotAppended() {
        show("Olivia ce parere")
        show("Olivia, ce părere ai despre raport?") // recognizer fixed punctuation and diacritics
        pause()
        assertEquals(listOf("Olivia, ce părere ai despre raport?"), utterances.map { it.second })
    }

    @Test
    fun replacedTextCommitsThePreviousOne() {
        show("Prima frază aici")
        show("Complet altceva acum")
        pause()
        assertEquals(listOf("Prima frază aici", "Complet altceva acum"), utterances.map { it.second })
    }

    @Test
    fun speakerChangeStartsNewUtterance() {
        show("Eu cred că e bine", speaker = "Radu")
        pause()
        show("Și eu sunt de acord", speaker = "Ana")
        pause()
        assertEquals(listOf("Radu" to "Eu cred că e bine", "Ana" to "Și eu sunt de acord"), utterances)
    }

    @Test
    fun fastTurnTakingKeepsEachSpeakersWords() {
        show("Eu cred că e bine", speaker = "Radu")
        show("Și eu sunt de acord", speaker = "Ana") // no pause in between
        pause()
        assertEquals(listOf("Radu" to "Eu cred că e bine", "Ana" to "Și eu sunt de acord"), utterances)
    }

    @Test
    fun staticLabelsNeverBecomeTheSource() {
        repeat(10) {
            assembler.observe(listOf(Region("zoom|button", "Dezactivează sunetul", null, explicit = false)))
            now += 300
        }
        assertNull(locked)
    }

    @Test
    fun frequentlyChangingSentencesAreLearnedAsSource() {
        show("Bună ziua la toți", key = "meet|TextView@12", explicit = false)
        show("Bună ziua la toți colegii", key = "meet|TextView@12", explicit = false)
        show("Bună ziua la toți colegii de azi", key = "meet|TextView@12", explicit = false)
        assertEquals("meet|TextView@12", locked)
        pause()
        assertEquals("Bună ziua la toți colegii de azi", utterances.single().second)
    }

    @Test
    fun newSentenceAfterFixedTextKeepsItsFirstWords() {
        // Seen on the emulator: a fixed title in the same element made the next sentence lose "Am vorbit".
        show("Titlu fix Bună")
        show("Titlu fix Bună ziua")
        show("Titlu fix Bună ziua tuturor.")
        pause()
        show("Titlu fix Am")
        show("Titlu fix Am vorbit")
        show("Titlu fix Am vorbit ieri.")
        pause()
        assertEquals("Am vorbit ieri.", utterances.last().second)
    }

    @Test
    fun meetSettingsMenuNeverBecomesTheSource() {
        // What real Meet showed: the options sheet changing as captions were turned on.
        val menu = listOf(
            "Filters out background noise and improves audio quality Watch only presentations",
            "Filters out background noise and improves audio quality Watch only presentations Show captions in this call English (United Kingdom)",
            "Filters out background noise and improves audio quality Watch only presentations Show captions in this call Romanian When off, your own reactions still appear",
            "Filters out background noise and improves audio quality Watch only presentations Show captions in this call Romanian When off, your own reactions still appear Reactions move on the screen",
        )
        menu.forEach { show(it, key = "meet|TextView@9", explicit = false) }
        assertNull(locked)
    }

    @Test
    fun textThatIsOnlyReplacedIsNotCaptions() {
        listOf("Ana vorbește acum", "Radu vorbește acum", "Maria vorbește acum", "Ion vorbește acum")
            .forEach { show(it, key = "meet|speaker_banner", explicit = false) }
        assertNull(locked)
    }

    @Test
    fun switchesFromAStalledSourceToGrowingCaptions() {
        show("Unu doi trei", key = "a", explicit = false)
        show("Unu doi trei patru", key = "a", explicit = false)
        show("Unu doi trei patru cinci", key = "a", explicit = false)
        assertEquals("a", locked)
        now += CaptionAssembler.SWITCH_NO_GROWTH_MS + 1_000 // "a" stops growing
        show("Bună ziua", key = "b", explicit = false)
        show("Bună ziua tuturor", key = "b", explicit = false)
        show("Bună ziua tuturor de aici", key = "b", explicit = false)
        assertEquals("b", locked)
    }

    @Test
    fun commitNowSavesTheQuestionStillOnScreen() {
        show("Cine poate")
        show("Cine poate spune definiția")
        assembler.commitNow() // "Ce să răspund?" pressed before the pause
        assertEquals("Cine poate spune definiția", utterances.single().second)
        show("Cine poate spune definiția la educație")
        pause()
        assertEquals(listOf("Cine poate spune definiția", "la educație"), utterances.map { it.second })
    }

    @Test
    fun alignHandlesGrowingLastWord() {
        assertEquals(0, align(listOf("Bună", "ziu"), listOf("Bună", "ziua", "tuturor")))
        assertEquals(2, align(listOf("a", "b", "c", "d"), listOf("c", "d", "e")))
        assertEquals(-1, align(listOf("unu", "doi"), listOf("trei", "patru")))
    }
}
