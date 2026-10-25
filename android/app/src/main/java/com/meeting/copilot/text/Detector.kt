package com.meeting.copilot.text

/**
 * Decides whether a caption line is about her: her name (tolerant of speech-to-text
 * misspellings and Romanian case endings), a question or request, or a topic she follows.
 */
class Detector(names: List<String>, topics: List<String>) {

    data class Result(val mentionsName: Boolean, val isQuestion: Boolean, val topic: String?)

    private val aliases: List<List<String>> = names
        .map { name -> TextNorm.words(name).map(TextNorm::key).filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .distinct()

    private val topicKeys: List<Pair<String, List<String>>> = topics
        .map { it.trim() }
        .filter { it.length >= 3 }
        .map { topic -> topic to TextNorm.words(topic).map(TextNorm::key).filter { it.isNotEmpty() } }
        .filter { it.second.isNotEmpty() }

    val hasNames: Boolean get() = aliases.isNotEmpty()

    fun analyze(text: String): Result {
        val tokens = TextNorm.words(text).map(TextNorm::key).filter { it.isNotEmpty() }
        return Result(
            mentionsName = mentionsName(tokens),
            isQuestion = isQuestion(text),
            topic = findTopic(tokens),
        )
    }

    private fun mentionsName(tokens: List<String>): Boolean = aliases.any { alias ->
        (0..tokens.size - alias.size).any { start ->
            alias.indices.all { i -> tokenMatches(tokens[start + i], alias[i]) }
        }
    }

    /** Each topic word may carry a Romanian ending: "raport lunar" matches "raportul lunar". */
    private fun findTopic(tokens: List<String>): String? = topicKeys.firstOrNull { (_, words) ->
        (0..tokens.size - words.size).any { start ->
            words.indices.all { i ->
                val token = tokens[start + i]
                val word = words[i]
                token == word || (word.length >= 4 && token.startsWith(word) && token.length - word.length <= 4)
            }
        }
    }?.first

    companion object {
        // Romanian case endings: Olivia -> Oliviei / Olivio, Radu -> Radule, Ana -> Anei.
        private val SUFFIXES = listOf("ei", "ul", "ule", "e", "o", "i", "a")

        fun tokenMatches(token: String, alias: String): Boolean {
            if (token == alias) return true
            if (alias.length >= 3) {
                if (SUFFIXES.any { token == alias + it }) return true
                val last = alias.last()
                if (last in "aeiou" && alias.length >= 4) {
                    val stem = alias.dropLast(1)
                    if (SUFFIXES.any { token == stem + it }) return true
                }
            }
            // One wrong letter, as speech-to-text often produces ("Olivea", "Livia" for short names is too risky).
            return alias.length >= 5 &&
                kotlin.math.abs(token.length - alias.length) <= 1 &&
                TextNorm.levenshtein(token, alias) <= 1
        }

        // Matched against lowercase text without diacritics.
        private val QUESTION_PATTERNS = listOf(
            "\\bce (crezi|parere|zici|spui|propui|stii|facem|urmeaza|ai de gand)\\b",
            "\\bcum (vezi|stam|facem|procedam|rezolvam|crezi|ar fi|merge|e cu)\\b",
            "\\bcand (crezi|poti|puteti|finalizam|trimitem|incepem|e gata|este gata|termini)\\b",
            "\\bcare (este|e|ar fi|sunt|e situatia)\\b",
            "\\bcine (se ocupa|preia|stie|trimite|face|poate|vrea|are|ne spune|imi spune)\\b",
            "\\b(poate|stie|vrea|are) cineva\\b",
            "\\bcineva (poate|stie|vrea|are)\\b",
            "\\bce (inseamna|reprezinta|intelegeti|intelegi)\\b",
            "\\b(spune|spuneti|da|dati|zi|ziceti|explica|explicati) (definitia|un exemplu|exemple)\\b",
            "\\b(explicati|enumerati|definiti)\\b",
            "\\b(poti|puteti|ai putea|ati putea|vrei|vreti) (sa|confirma|verifica|prelua|trimite|spune|ajuta)\\b",
            "\\b(esti|sunteti) de acord\\b",
            "\\b(ai|ati) (primit|vazut|trimis|timp|reusit|apucat|terminat|verificat)\\b",
            "\\bne (auzi|auziti|poti spune|puteti spune|spui|spuneti|zici)\\b",
            "\\b(esti|sunteti) (pe mute|cu noi|acolo|disponibila|disponibil)\\b",
            "\\b(te|va) (rog|rugam)\\b",
            "\\b(spune|spuneti|zi|ziceti)-ne\\b",
            "\\b(ce|cum|cand|unde|cine|care|cat|cate|de ce)\\b.*\\b(tu|dumneavoastra|dvs)\\b",
            "\\?",
        ).map { Regex(it) }

        private val ENGLISH = setOf(
            "the", "and", "you", "is", "to", "of", "that", "we", "it", "this", "are", "have", "for", "what",
            "with", "be", "can", "so", "just", "do", "not", "will", "they", "about",
        )
        private val ROMANIAN = setOf(
            "si", "este", "ca", "nu", "pe", "de", "la", "sa", "cu", "un", "ce", "mai", "am", "ne", "sunt",
            "pentru", "asta", "care", "dar", "vom", "acum", "foarte", "trebuie",
        )

        /** True when the captions are clearly in English (wrong caption language in Zoom/Meet). */
        fun looksEnglish(texts: List<String>): Boolean {
            var english = 0
            var romanian = 0
            for (word in texts.flatMap { TextNorm.words(it) }.map(TextNorm::key)) {
                if (word in ENGLISH) english++
                if (word in ROMANIAN) romanian++
            }
            return english >= 6 && english > romanian * 2
        }

        fun isQuestion(text: String): Boolean {
            val normalized = TextNorm.norm(text)
            return QUESTION_PATTERNS.any { it.containsMatchIn(normalized) }
        }

        /** Words that open a question when they start a sentence ("Ce rol are…", "Unde se află…"). */
        private val QUESTION_STARTS = setOf(
            "ce", "cum", "cand", "unde", "cine", "care", "cat", "cata", "cati", "cate", "oare", "cumva",
            "poti", "puteti", "poate", "stiti", "stii", "stie", "aveti", "exista", "sunteti", "vreti", "vrei",
        )
        private val QUESTION_STARTS_2 = setOf(
            "de ce", "in ce", "la ce", "pentru ce", "din ce", "cu ce", "despre ce", "in care", "la care", "cat de",
            "ati auzit", "ati citit", "ai citit", "ati vazut", "ai vazut", "se poate", "e adevarat", "este adevarat",
        )
        /** Sentences that start like a question but aren't one ("Cum am spus…"). */
        private val NOT_QUESTION_STARTS = listOf(
            "cum am spus", "cum spuneam", "cum ziceam", "cum v-am spus", "cum stiti", "ce bine", "ce frumos",
            "cand am", "cand eram", "unde am", "cat timp", "care va sa zica", "cat despre",
        )
        /** A task the teacher gives out loud ("Explicați…", "Dați un exemplu…"). */
        private val ASK_VERBS = Regex(
            "\\b(precizati|argumentati|comparati|descrieti|identificati|numiti|enumerati|explicati|definiti|justificati|" +
                "analizati|dati exemple|dati un exemplu|spuneti-mi|spune-mi|ce parere aveti|ce credeti)\\b"
        )
        private val SENTENCE_END = Regex("[.!?…\\n]+")

        /**
         * Wider than [isQuestion]: anything that may be a question, including a sentence that just
         * starts like one. The AI then confirms it before she is alerted, so a false hit costs nothing.
         */
        fun maybeQuestion(text: String): Boolean {
            if (isQuestion(text)) return true
            val normalized = TextNorm.norm(text)
            if (ASK_VERBS.containsMatchIn(normalized)) return true
            return normalized.split(SENTENCE_END).map { it.trim() }.any { sentence ->
                val words = sentence.split(' ').filter { it.isNotEmpty() }
                words.size >= 3 && NOT_QUESTION_STARTS.none { sentence.startsWith(it) } &&
                    (words[0] in QUESTION_STARTS || "${words[0]} ${words[1]}" in QUESTION_STARTS_2)
            }
        }

        private val ABOUT_SCREEN = Regex(
            "\\b(slide\\w*|diapozitiv\\w*|ecran\\w*|prezentar\\w*|imagin\\w*|poza\\w*|fotografi\\w*|grafic\\w*|diagram\\w*|" +
                "tabel\\w*|figur\\w*|schem\\w*|harta|hartii|formul\\w*|vedeti|vezi|observati|observi|scrie|scris|pagina)\\b"
        )

        /** The question points at what is shown on screen, so the AI needs a screenshot. */
        fun aboutScreen(text: String): Boolean = ABOUT_SCREEN.containsMatchIn(TextNorm.norm(text))
    }
}
