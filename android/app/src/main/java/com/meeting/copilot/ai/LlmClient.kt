package com.meeting.copilot.ai

import com.meeting.copilot.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class LlmException(message: String) : Exception(message)

/** Calls Gemini or OpenRouter directly from the phone and returns the model's JSON reply. */
class LlmClient(private val prefs: Prefs) {

    fun isConfigured(): Boolean = prefs.hasAiKey()

    /** Until when a provider is out of quota (daily free limit, or no credit), and why. */
    private val blockedUntil = ConcurrentHashMap<String, Long>()
    private val blockedReason = ConcurrentHashMap<String, String>()

    /**
     * Sends one request. When the chosen provider is out of quota and the other provider's key
     * is filled in too, it uses that one instead; an exhausted provider isn't asked again until
     * its limit resets, so the app doesn't fail every 90 seconds.
     */
    suspend fun json(system: String, user: String, maxTokens: Int, timeoutMs: Int = 25_000, imageJpeg: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) throw LlmException("Lipsește cheia AI (Setări → AI).")
            var lastError: String? = null
            for (provider in providerOrder()) {
                if (System.currentTimeMillis() < (blockedUntil[provider] ?: 0L)) {
                    lastError = blockedReason[provider]
                    continue
                }
                try {
                    return@withContext call(provider, system, user, maxTokens, timeoutMs, imageJpeg)
                } catch (e: QuotaException) {
                    blockedUntil[provider] = System.currentTimeMillis() + e.waitMs
                    blockedReason[provider] = e.message.orEmpty()
                    lastError = e.message
                }
            }
            throw LlmException(lastError ?: "Serviciul AI nu e disponibil acum.")
        }

    /** The chosen provider first, the other one as backup when its key is also filled in. */
    private fun providerOrder(): List<String> {
        val primary = prefs.provider
        val backup = if (primary == Prefs.PROVIDER_OPENROUTER) Prefs.PROVIDER_GEMINI else Prefs.PROVIDER_OPENROUTER
        val backupKey = if (backup == Prefs.PROVIDER_GEMINI) prefs.geminiKey else prefs.openRouterKey
        return if (backupKey.isNotBlank()) listOf(primary, backup) else listOf(primary)
    }

    private suspend fun call(provider: String, system: String, user: String, maxTokens: Int, timeoutMs: Int, imageJpeg: String?): JSONObject {
        var image = imageJpeg
        var attempt = 0
        var switchedModel = false
        while (true) {
            try {
                val text = if (provider == Prefs.PROVIDER_OPENROUTER) {
                    openRouter(system, user, maxTokens, timeoutMs, image)
                } else {
                    gemini(system, user, maxTokens, timeoutMs, image)
                }
                return parseJson(text)
            } catch (e: LlmException) {
                // A model that can't take images still answers from the words alone.
                if (image == null || e.message?.startsWith("Cerere respinsă") != true) throw e
                image = null
            } catch (e: RetryableException) {
                if (attempt++ >= 1) throw LlmException(e.message ?: "Serviciul AI e ocupat.")
                delay(1_500)
            } catch (e: ModelNotFoundException) {
                // OpenRouter answers 404 when the model can't take images: retry with words only.
                if (image != null) {
                    image = null
                    continue
                }
                // Google retires models regularly: switch to its newest Flash model and retry once.
                val replacement = if (provider == Prefs.PROVIDER_GEMINI && !switchedModel) currentGeminiFlash() else null
                if (replacement == null || replacement == prefs.geminiModel.trim()) {
                    throw LlmException("Modelul nu mai e disponibil: ${e.message?.take(120)}. Schimbă numele modelului în Setări.")
                }
                prefs.geminiModel = replacement
                switchedModel = true
            }
        }
    }

    private class RetryableException(message: String) : Exception(message)
    private class ModelNotFoundException(message: String) : Exception(message)
    private class QuotaException(message: String, val waitMs: Long) : Exception(message)

    /** Asks Gemini which models this key can use and picks the newest regular Flash model. */
    private fun currentGeminiFlash(): String? = try {
        val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("x-goog-api-key", prefs.geminiKey.trim())
        }
        try {
            if (connection.responseCode != 200) null
            else pickFlashModel(JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }))
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun gemini(system: String, user: String, maxTokens: Int, timeoutMs: Int, imageJpeg: String?): String {
        val model = prefs.geminiModel.trim().ifEmpty { "gemini-3.8-flash" }
        val generation = JSONObject()
            .put("temperature", 0.3)
            .put("maxOutputTokens", maxTokens)
            .put("responseMimeType", "application/json")
        // 2.5 Flash models "think" by default, which adds seconds; these short tasks don't need it.
        if (model.contains("2.5-flash")) generation.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().apply {
                        put(JSONObject().put("text", user))
                        if (imageJpeg != null) {
                            put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageJpeg)))
                        }
                    })
                )
            )
            .put("generationConfig", generation)
        val response = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to prefs.geminiKey.trim()),
            body,
            timeoutMs,
        )
        val parts = response.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw LlmException("Gemini nu a returnat niciun răspuns (posibil blocat de filtre).")
        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (!part.optBoolean("thought")) append(part.optString("text"))
            }
        }
    }

    private fun openRouter(system: String, user: String, maxTokens: Int, timeoutMs: Int, imageJpeg: String?): String {
        val model = prefs.openRouterModel.trim().ifEmpty { "anthropic/claude-haiku-5.5" }
        val userContent: Any = if (imageJpeg == null) user else JSONArray()
            .put(JSONObject().put("type", "text").put("text", user))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageJpeg")))
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.3)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", userContent))
            )
        // Strict JSON mode keeps replies parseable; a model that doesn't support it is remembered.
        val jsonMode = model !in noJsonMode
        if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        val response = try {
            post(
                "https://openrouter.ai/api/v1/chat/completions",
                mapOf(
                    "Authorization" to "Bearer ${prefs.openRouterKey.trim()}",
                    "X-Title" to "Asistent Sedinte",
                ),
                body,
                timeoutMs,
            )
        } catch (e: LlmException) {
            if (!jsonMode || e.message?.contains("response_format", ignoreCase = true) != true) throw e
            noJsonMode.add(model)
            return openRouter(system, user, maxTokens, timeoutMs, imageJpeg)
        }
        return response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw LlmException("OpenRouter nu a returnat niciun răspuns.")
    }

    private val noJsonMode = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun post(url: String, headers: Map<String, String>, body: JSONObject, timeoutMs: Int): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) return JSONObject(text)
            val message = errorMessage(text)
            val gemini = url.contains("googleapis")
            when {
                code == 402 -> throw QuotaException("Contul OpenRouter nu mai are credit. Adaugă credit pe openrouter.ai.", 30 * 60_000L)
                code == 429 && (message.contains("quota", true) || message.contains("credit", true)) -> {
                    val wait = retryDelayMs(message) ?: if (message.contains("minute", true)) 60_000L else 3_600_000L
                    throw QuotaException(quotaMessage(gemini, wait), wait)
                }
                code == 429 -> throw RetryableException("Prea multe cereri deodată la serviciul AI.")
                code >= 500 -> throw RetryableException("Serviciul AI are o problemă ($code).")
                code == 404 -> throw ModelNotFoundException(message)
                code == 400 -> throw LlmException("Cerere respinsă: ${message.take(120)}. Verifică numele modelului.")
                code == 401 || code == 403 -> throw LlmException("Cheia AI nu e acceptată. Verific-o în Setări → AI.")
                else -> throw LlmException("Eroare AI $code: ${message.take(120)}")
            }
        } catch (e: SocketTimeoutException) {
            throw RetryableException("Serviciul AI nu a răspuns la timp.")
        } catch (e: UnknownHostException) {
            throw LlmException("Fără internet.")
        } catch (e: IOException) {
            throw LlmException("Eroare de rețea: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(text: String): String = try {
        val json = JSONObject(text)
        json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: text.take(200)
    } catch (_: Exception) {
        text.take(200)
    }

    private fun quotaMessage(gemini: Boolean, waitMs: Long): String {
        val hours = waitMs / 3_600_000
        val back = if (hours >= 1) "revine în ~$hours h" else "revine în ~${(waitMs / 60_000).coerceAtLeast(1)} min"
        return if (gemini) {
            "Limita gratuită Gemini s-a terminat ($back). Adaugă o cheie OpenRouter în Setări → AI ca să meargă mai departe."
        } else {
            "Limita OpenRouter a fost atinsă ($back)."
        }
    }

    companion object {
        private val FLASH_VERSION = Regex("^gemini-(\\d+(?:\\.\\d+)?)-flash(-preview.*)?$")
        private val RETRY_IN = Regex("retry in (?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:([\\d.]+)s)?", RegexOption.IGNORE_CASE)

        /** "Please retry in 13h38m11.02s." -> milliseconds; null when the reply doesn't say. */
        fun retryDelayMs(message: String): Long? {
            val match = RETRY_IN.find(message) ?: return null
            val (h, m, s) = match.destructured
            val ms = (h.toLongOrNull() ?: 0) * 3_600_000 + (m.toLongOrNull() ?: 0) * 60_000 +
                ((s.toDoubleOrNull() ?: 0.0) * 1000).toLong()
            return ms.takeIf { it > 0 }
        }

        /**
         * From a Gemini ListModels reply, the newest "gemini-X-flash" that supports
         * generateContent; stable versions beat previews; lite/image/audio variants are skipped.
         */
        fun pickFlashModel(list: JSONObject): String? {
            val models = list.optJSONArray("models") ?: return null
            data class Candidate(val id: String, val version: Double, val preview: Boolean)
            val candidates = (0 until models.length()).mapNotNull { i ->
                val model = models.optJSONObject(i) ?: return@mapNotNull null
                val id = model.optString("name").removePrefix("models/")
                val methods = model.optJSONArray("supportedGenerationMethods")
                val generates = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                val match = FLASH_VERSION.matchEntire(id) ?: return@mapNotNull null
                if (!generates) return@mapNotNull null
                Candidate(id, match.groupValues[1].toDouble(), match.groupValues[2].isNotEmpty())
            }
            return candidates.sortedWith(compareBy<Candidate> { it.preview }.thenByDescending { it.version }).firstOrNull()?.id
        }

        /** Accepts plain JSON, JSON inside ``` fences, or JSON with text around it. */
        fun parseJson(text: String): JSONObject {
            val start = text.indexOf('{')
            if (start < 0) throw LlmException("Răspunsul AI nu a avut formatul așteptat. Încearcă din nou.")
            val end = text.lastIndexOf('}')
            if (end > start) {
                try {
                    return JSONObject(text.substring(start, end + 1))
                } catch (_: Exception) {
                }
            }
            // The reply was cut off (length limit): close what's open so the fields so far aren't lost.
            return try {
                JSONObject(repairTruncatedJson(text.substring(start)))
            } catch (_: Exception) {
                throw LlmException("Răspunsul AI nu a avut formatul așteptat. Încearcă din nou.")
            }
        }

        /** Closes an unfinished string, drops a dangling key or comma, and closes open arrays/objects. */
        fun repairTruncatedJson(text: String): String {
            val out = StringBuilder()
            val stack = ArrayDeque<Char>()
            var inString = false
            var escaped = false
            for (c in text) {
                out.append(c)
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> if (stack.isNotEmpty()) stack.removeLast()
                }
            }
            if (escaped) out.setLength(out.length - 1)
            if (inString) out.append('"')
            var repaired = out.toString().trimEnd()
            // A key without a value ("answer":) or a trailing comma would still be invalid.
            repaired = repaired.replace(Regex(",\\s*\"[^\"]*\"\\s*:?\\s*$"), "").trimEnd().removeSuffix(",")
            if (repaired.endsWith(":")) repaired += "null"
            return repaired + stack.reversed().joinToString("")
        }
    }
}
