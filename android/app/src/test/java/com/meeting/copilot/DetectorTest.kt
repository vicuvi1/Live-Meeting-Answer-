package com.meeting.copilot

import com.meeting.copilot.ai.LlmClient
import com.meeting.copilot.ai.Prompts
import com.meeting.copilot.text.Detector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {
    private val detector = Detector(listOf("Olivia", "doamna Popescu"), listOf("buget", "raport lunar"))

    @Test
    fun directQuestionWithDiacriticsOrWithout() {
        val a = detector.analyze("Olivia, ce părere ai despre termen?")
        assertTrue(a.mentionsName && a.isQuestion)
        val b = detector.analyze("olivia ce parere ai")
        assertTrue(b.mentionsName && b.isQuestion)
    }

    @Test
    fun speechToTextMisspellingsAndCaseEndings() {
        assertTrue(detector.analyze("Olivea poți confirma").mentionsName)
        assertTrue(detector.analyze("Am vorbit cu Oliviei ieri").mentionsName)
        assertTrue(detector.analyze("Doamna Popescu, ne auziți?").mentionsName)
        assertFalse(detector.analyze("Olimpia a trimis raportul").mentionsName)
        assertFalse(detector.analyze("Popescu de la vânzări").mentionsName)
    }

    @Test
    fun questionsWithoutQuestionMark() {
        assertTrue(Detector.isQuestion("poti sa ne trimiti situatia pana vineri"))
        assertTrue(Detector.isQuestion("esti de acord cu propunerea"))
        assertTrue(Detector.isQuestion("te rog să verifici cifrele"))
        assertFalse(Detector.isQuestion("am trimis situația ieri"))
    }

    @Test
    fun detectsEnglishCaptions() {
        assertTrue(Detector.looksEnglish(listOf("So we will talk about the budget and what you can do for this project")))
        assertFalse(Detector.looksEnglish(listOf("Bună ziua, acum vorbim despre buget și ce trebuie să facem pentru proiect")))
        assertFalse(Detector.looksEnglish(listOf("Avem meeting la ora trei, e ok pentru tine?")))
    }

    @Test
    fun topics() {
        assertEquals("buget", detector.analyze("Bugetul pe trimestrul trei e depășit").topic)
        assertEquals("raport lunar", detector.analyze("Despre raportul lunar vorbim mâine").topic)
        assertNull(detector.analyze("Vremea e frumoasă").topic)
    }

    @Test
    fun parsesJsonWrappedInFences() {
        val json = LlmClient.parseJson("```json\n{\"topic\":\"Buget\",\"points\":[\"a\"],\"for_me\":\"\"}\n```")
        val (cleaned, catchUp) = Prompts.parseDigest(json, freshCount = 0, now = 1)
        assertTrue(cleaned.isEmpty())
        assertEquals("Buget", catchUp.topic)
        assertEquals(listOf("a"), catchUp.points)
    }

    @Test
    fun digestIgnoresOutOfRangeLineNumbers() {
        val json = LlmClient.parseJson("{\"cleaned\":[{\"i\":1,\"text\":\"Bună ziua.\"},{\"i\":9,\"text\":\"x\"}],\"topic\":\"t\",\"points\":[]}")
        val (cleaned, _) = Prompts.parseDigest(json, freshCount = 2, now = 1)
        assertEquals(mapOf(0 to "Bună ziua."), cleaned)
    }

    @Test
    fun picksNewestStableFlashModel() {
        val list = LlmClient.parseJson(
            """{"models":[
              {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
              {"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent","countTokens"]},
              {"name":"models/gemini-4.0-flash-preview-11-2026","supportedGenerationMethods":["generateContent"]},
              {"name":"models/gemini-3.9-flash-lite","supportedGenerationMethods":["generateContent"]},
              {"name":"models/gemini-3.9-flash-image","supportedGenerationMethods":["generateContent"]},
              {"name":"models/gemini-3.5-pro","supportedGenerationMethods":["generateContent"]},
              {"name":"models/text-embedding-005","supportedGenerationMethods":["embedContent"]}
            ]}"""
        )
        assertEquals("gemini-3.8-flash", LlmClient.pickFlashModel(list))
    }

    @Test
    fun repairsAReplyCutOffMidSentence() {
        // The exact cut-off reply from the real Meet test.
        val cut = "{\"question\":\"Victor întreabă cum stă Olivia cu crema pentru acasă\",\"context\":[\"Subtitrările sunt acum disponibile în mai"
        val json = LlmClient.parseJson(cut)
        assertEquals("Victor întreabă cum stă Olivia cu crema pentru acasă", json.getString("question"))
        val answer = Prompts.parseAnswer(json, "trigger", 1)
        assertEquals(Prompts.SAFE_LINE, answer.answer) // no answer yet: safe line instead of an error
    }

    @Test
    fun keepsAPartialAnswer() {
        val json = LlmClient.parseJson("{\"question\":\"Când e gata?\",\"answer\":\"Raportul e gata vineri")
        assertEquals("Raportul e gata vineri", json.getString("answer"))
        assertEquals(1, LlmClient.parseJson("{\"a\":1,\"answer\"").length())
        assertTrue(LlmClient.parseJson("{\"answer\":").isNull("answer"))
    }

    @Test
    fun readsRetryDelayFromQuotaErrors() {
        assertEquals((13 * 3600 + 38 * 60 + 11) * 1000L + 23, LlmClient.retryDelayMs("Please retry in 13h38m11.023975365s."))
        assertEquals(20_000L, LlmClient.retryDelayMs("Quota exceeded. Please retry in 20s."))
        assertEquals(null, LlmClient.retryDelayMs("Quota exceeded for metric."))
    }

    @Test
    fun answerFallsBackToSafeLine() {
        val answer = Prompts.parseAnswer(LlmClient.parseJson("{\"question\":\"Când e gata?\",\"confident\":false}"), "trigger", 5)
        assertEquals(Prompts.SAFE_LINE, answer.answer)
        assertFalse(answer.confident)
    }
}
