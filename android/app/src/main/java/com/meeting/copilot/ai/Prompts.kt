package com.meeting.copilot.ai

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A transcript line as the prompts see it. */
data class PromptLine(val ts: Long, val speaker: String?, val text: String)

data class CatchUp(
    val topic: String,
    val points: List<String>,
    val forMe: String,
    val updatedAt: Long,
)

data class Answer(
    val trigger: String,
    val question: String,
    val context: List<String>,
    val answer: String,
    val confident: Boolean,
    val createdAt: Long,
)

data class Task(val task: String, val from: String, val deadline: String)

data class Summary(
    val title: String,
    val points: List<String>,
    val decisions: List<String>,
    val myTasks: List<Task>,
    val myPromises: List<String>,
)

data class Profile(val name: String, val notes: String, val vocabulary: String)

object Prompts {
    private val clock = SimpleDateFormat("HH:mm", Locale.US)

    /** Said when the AI has no facts: commits to nothing. */
    const val SAFE_LINE = "Îmi notez și revin cu detaliile exacte pe e-mail imediat după ședință."

    fun format(line: PromptLine): String =
        "[${clock.format(Date(line.ts))}] ${line.speaker?.let { "$it: " } ?: ""}${line.text}"

    private fun profileBlock(p: Profile): String = buildString {
        append("Despre ${p.name}: ").append(p.notes.ifBlank { "(fără notițe)" }).append('\n')
        if (p.vocabulary.isNotBlank()) append("Vocabular (nume de colegi, proiecte, termeni): ").append(p.vocabulary).append('\n')
    }

    // ---- Rolling digest: cleans new caption lines and updates "what did I miss" ----

    fun digestSystem(name: String) = """
Ești asistentul discret al utilizatoarei $name într-o ședință online în limba română. Primești subtitrări automate, care conțin greșeli de recunoaștere vocală.
Ai două sarcini:
1. Corectează fiecare replică nouă: repară cuvintele recunoscute greșit, numele (folosind vocabularul), diacriticele și punctuația. Nu schimba sensul, nu adăuga nimic, nu rezuma. Dacă nu ești sigur de o corectură, lasă textul așa.
2. Actualizează rezumatul pentru $name, care nu a fost atentă și trebuie să se pună la curent în 10 secunde.
Răspunde DOAR cu JSON, exact în forma:
{"cleaned":[{"i":1,"text":"replica corectată"}],"topic":"despre ce se discută ACUM, max 12 cuvinte","points":["max 3 lucruri importante recente: decizii, cifre, nume, termene; max 15 cuvinte fiecare"],"for_me":"ce i s-a cerut sau ce se așteaptă de la $name, cu cine și până când; șir gol dacă nimic"}
""".trim()

    fun digestUser(profile: Profile, previous: CatchUp?, context: List<PromptLine>, fresh: List<PromptLine>) = buildString {
        append(profileBlock(profile))
        if (previous != null) {
            append("Rezumat anterior: ").append(previous.topic)
            previous.points.forEach { append(" | ").append(it) }
            if (previous.forMe.isNotBlank()) append(" | Pentru ea: ").append(previous.forMe)
            append('\n')
        }
        append("\nContext recent (deja corectat):\n")
        if (context.isEmpty()) append("(nimic)\n") else context.forEach { append(format(it)).append('\n') }
        append("\nReplici noi de corectat:\n")
        fresh.forEachIndexed { i, line -> append(i + 1).append(". ").append(format(line)).append('\n') }
    }

    fun parseDigest(json: JSONObject, freshCount: Int, now: Long): Pair<Map<Int, String>, CatchUp> {
        val cleaned = HashMap<Int, String>()
        json.optJSONArray("cleaned")?.let { array ->
            for (k in 0 until array.length()) {
                val item = array.optJSONObject(k) ?: continue
                val index = item.optInt("i", -1) - 1
                val text = item.optString("text").trim()
                if (index in 0 until freshCount && text.isNotEmpty()) cleaned[index] = text
            }
        }
        val catchUp = CatchUp(
            topic = json.optString("topic").trim(),
            points = strings(json.optJSONArray("points")).take(3),
            forMe = json.optString("for_me").trim(),
            updatedAt = now,
        )
        return cleaned to catchUp
    }

    // ---- Instant answer when she is asked something ----

    fun answerSystem(name: String) = """
Ești asistentul discret al utilizatoarei $name într-o ședință online în limba română. $name tocmai a fost întrebată ceva și trebuie să răspundă ACUM, cu voce tare.
Reguli:
- Identifică exact ce i s-a cerut ei.
- Răspunsul: 1–2 fraze naturale, la persoana I, exact cum le-ar spune ea, politicos și sigur pe sine.
- Folosește DOAR fapte din notițele ei sau din discuție. NU inventa cifre, date, statusuri sau promisiuni.
- Dacă îți lipsesc faptele, pune "confident": false și dă un răspuns sigur, care nu promite nimic concret (că verifică și revine), legat de subiectul întrebării.
Răspunde DOAR cu JSON, exact în forma:
{"question":"ce a fost întrebată, max 15 cuvinte","context":["max 2 lucruri spuse înainte care contează pentru răspuns"],"answer":"ce să spună","confident":true}
""".trim()

    fun answerUser(profile: Profile, catchUp: CatchUp?, recent: List<PromptLine>, trigger: PromptLine) = buildString {
        append(profileBlock(profile))
        if (catchUp != null && catchUp.topic.isNotBlank()) {
            append("Rezumatul ședinței până acum: ").append(catchUp.topic)
            catchUp.points.forEach { append(" | ").append(it) }
            append('\n')
        }
        append("\nUltimele minute din ședință:\n")
        recent.forEach { append(format(it)).append('\n') }
        append("\nReplica la care trebuie să răspundă: ").append(format(trigger)).append('\n')
    }

    fun parseAnswer(json: JSONObject, trigger: String, now: Long) = Answer(
        trigger = trigger,
        question = json.optString("question").trim().ifEmpty { trigger },
        context = strings(json.optJSONArray("context")).take(2),
        answer = json.optString("answer").trim().ifEmpty { SAFE_LINE },
        confident = json.optBoolean("confident", false),
        createdAt = now,
    )

    // ---- Summary after the meeting ----

    fun summarySystem(name: String) = """
Rezumă ședința pentru $name. Include doar ce reiese clar din transcriere; liste goale dacă nu există.
Răspunde DOAR cu JSON, exact în forma:
{"title":"titlu scurt, max 6 cuvinte","summary":["3–6 puncte principale"],"decisions":["decizii luate"],"my_tasks":[{"task":"ce trebuie să facă $name","from":"cine a cerut","deadline":"termen sau șir gol"}],"my_promises":["ce a spus $name că va face"]}
""".trim()

    fun summaryUser(profile: Profile, lines: List<PromptLine>, maxChars: Int = 120_000): String {
        val transcript = lines.joinToString("\n") { format(it) }.takeLast(maxChars)
        return profileBlock(profile) + "\nTranscrierea ședinței:\n" + transcript
    }

    fun parseSummary(json: JSONObject): Summary {
        val tasks = mutableListOf<Task>()
        json.optJSONArray("my_tasks")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val task = item.optString("task").trim()
                if (task.isNotEmpty()) tasks.add(Task(task, item.optString("from").trim(), item.optString("deadline").trim()))
            }
        }
        return Summary(
            title = json.optString("title").trim().ifEmpty { "Ședință" },
            points = strings(json.optJSONArray("summary")),
            decisions = strings(json.optJSONArray("decisions")),
            myTasks = tasks,
            myPromises = strings(json.optJSONArray("my_promises")),
        )
    }

    fun summaryToJson(s: Summary): String = JSONObject()
        .put("title", s.title)
        .put("summary", JSONArray(s.points))
        .put("decisions", JSONArray(s.decisions))
        .put("my_tasks", JSONArray().apply {
            s.myTasks.forEach { put(JSONObject().put("task", it.task).put("from", it.from).put("deadline", it.deadline)) }
        })
        .put("my_promises", JSONArray(s.myPromises))
        .toString()

    fun summaryFromJson(text: String?): Summary? = try {
        if (text.isNullOrBlank()) null else parseSummary(JSONObject(text))
    } catch (_: Exception) {
        null
    }

    /** Plain text to share to Telegram / WhatsApp / e-mail. */
    fun shareText(title: String, date: String, s: Summary?, lines: List<PromptLine>): String = buildString {
        append(title).append(" · ").append(date).append("\n\n")
        if (s != null) {
            if (s.myTasks.isNotEmpty()) {
                append("SARCINILE MELE\n")
                s.myTasks.forEach { t ->
                    append("• ").append(t.task)
                    if (t.from.isNotBlank()) append(" (cerut de ").append(t.from).append(')')
                    if (t.deadline.isNotBlank()) append(" — termen: ").append(t.deadline)
                    append('\n')
                }
                append('\n')
            }
            if (s.myPromises.isNotEmpty()) {
                append("CE AM PROMIS\n")
                s.myPromises.forEach { append("• ").append(it).append('\n') }
                append('\n')
            }
            if (s.points.isNotEmpty()) {
                append("REZUMAT\n")
                s.points.forEach { append("• ").append(it).append('\n') }
                append('\n')
            }
            if (s.decisions.isNotEmpty()) {
                append("DECIZII\n")
                s.decisions.forEach { append("• ").append(it).append('\n') }
                append('\n')
            }
        }
        if (lines.isNotEmpty()) {
            append("TRANSCRIERE\n")
            lines.forEach { append(format(it)).append('\n') }
        }
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optString(i).trim().takeIf { it.isNotEmpty() } }
    }
}
