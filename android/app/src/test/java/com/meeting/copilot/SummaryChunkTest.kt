package com.meeting.copilot

import com.meeting.copilot.ai.PromptLine
import com.meeting.copilot.ai.Prompts
import com.meeting.copilot.ai.Summary
import com.meeting.copilot.ai.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryChunkTest {

    /** About three hours of talk: one line every 5 seconds. */
    private val longMeeting = (0 until 2_200).map { i ->
        PromptLine(1_700_000_000_000L + i * 5_000L, "Vorbitor ${i % 4}", "Replica numărul $i despre proiect, termene și buget pentru trimestrul următor.")
    }

    @Test
    fun longMeetingIsSplitWithoutLosingAnyLine() {
        val chunks = Prompts.chunkTranscript(longMeeting)
        assertTrue("a 3 h meeting needs several parts", chunks.size > 1)
        assertEquals(longMeeting, chunks.flatten())
        chunks.forEach { chunk ->
            val size = chunk.sumOf { Prompts.format(it).length + 1 }
            assertTrue("part of $size chars", size <= Prompts.SUMMARY_CHUNK_CHARS)
        }
    }

    @Test
    fun shortMeetingStaysInOnePart() {
        val chunks = Prompts.chunkTranscript(longMeeting.take(50))
        assertEquals(1, chunks.size)
        assertEquals(50, chunks[0].size)
    }

    @Test
    fun emptyTranscriptHasNoParts() {
        assertTrue(Prompts.chunkTranscript(emptyList()).isEmpty())
    }

    @Test
    fun localMergeKeepsEverythingFromEveryPart() {
        val a = Summary("Buget", listOf("p1", "p2"), listOf("d1"), listOf(Task("raport", "Ana", "vineri")), listOf("trimit mailul"))
        val b = Summary("Altceva", listOf("p2", "p3"), listOf("d2"), listOf(Task("raport", "Ana", "vineri"), Task("slide", "Ion", "")), emptyList())
        val merged = Prompts.mergeLocally(listOf(a, b))
        assertEquals("Buget", merged.title)
        assertEquals(listOf("p1", "p2", "p3"), merged.points)
        assertEquals(listOf("d1", "d2"), merged.decisions)
        assertEquals(listOf("raport", "slide"), merged.myTasks.map { it.task })
        assertEquals(listOf("trimit mailul"), merged.myPromises)
    }
}
