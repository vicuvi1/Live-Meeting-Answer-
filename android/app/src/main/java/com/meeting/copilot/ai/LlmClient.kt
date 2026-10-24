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

class LlmException(message: String) : Exception(message)

/** Calls Gemini or OpenRouter directly from the phone and returns the model's JSON reply. */
class LlmClient(private val prefs: Prefs) {

    fun isConfigured(): Boolean = prefs.hasAiKey()

    suspend fun json(system: String, user: String, maxTokens: Int, timeoutMs: Int = 25_000): JSONObject =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) throw LlmException("Lipsește cheia AI (Setări → AI).")
            var attempt = 0
            while (true) {
                try {
                    val text = if (prefs.provider == Prefs.PROVIDER_OPENROUTER) {
                        openRouter(system, user, maxTokens, timeoutMs)
                    } else {
                        gemini(system, user, maxTokens, timeoutMs)
                    }
                    return@withContext parseJson(text)
                } catch (e: RetryableException) {
                    if (attempt++ >= 1) throw LlmException(e.message ?: "Serviciul AI e ocupat.")
                    delay(1_500)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException()
        }

    private class RetryableException(message: String) : Exception(message)

    private fun gemini(system: String, user: String, maxTokens: Int, timeoutMs: Int): String {
        val model = prefs.geminiModel.trim().ifEmpty { "gemini-2.5-flash" }
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
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
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

    private fun openRouter(system: String, user: String, maxTokens: Int, timeoutMs: Int): String {
        val model = prefs.openRouterModel.trim().ifEmpty { "google/gemini-2.5-flash" }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.3)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        val response = post(
            "https://openrouter.ai/api/v1/chat/completions",
            mapOf(
                "Authorization" to "Bearer ${prefs.openRouterKey.trim()}",
                "X-Title" to "Asistent Sedinte",
            ),
            body,
            timeoutMs,
        )
        return response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw LlmException("OpenRouter nu a returnat niciun răspuns.")
    }

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
            when {
                code == 429 -> throw RetryableException("Limită de cereri atinsă la serviciul AI ($message).")
                code >= 500 -> throw RetryableException("Serviciul AI are o problemă ($code).")
                code == 400 || code == 404 -> throw LlmException("Cerere respinsă ($code): $message. Verifică numele modelului.")
                code == 401 || code == 403 -> throw LlmException("Cheia AI nu e acceptată ($code): $message")
                else -> throw LlmException("Eroare AI $code: $message")
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

    companion object {
        /** Accepts plain JSON, JSON inside ``` fences, or JSON with text around it. */
        fun parseJson(text: String): JSONObject {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw LlmException("Răspuns AI fără JSON: ${text.take(120)}")
            return try {
                JSONObject(text.substring(start, end + 1))
            } catch (e: Exception) {
                throw LlmException("Răspuns AI invalid: ${e.message}")
            }
        }
    }
}
