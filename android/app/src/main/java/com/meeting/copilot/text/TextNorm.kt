package com.meeting.copilot.text

import java.text.Normalizer
import java.util.Locale

/** Romanian-friendly text helpers: captions arrive with or without diacritics. */
object TextNorm {
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]")
    private val SPACES = Regex("\\s+")

    /** "Ședință, ţară" -> "Sedinta, tara" */
    fun stripDiacritics(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")

    /** Lowercase, no diacritics. */
    fun norm(text: String): String = stripDiacritics(text).lowercase(Locale.ROOT)

    fun words(text: String): List<String> = text.trim().split(SPACES).filter { it.isNotBlank() }

    /** Comparable form of one word: "Bugetul," -> "bugetul" */
    fun key(word: String): String = NON_WORD.replace(norm(word), "")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
