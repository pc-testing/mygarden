package com.pctesting.mygarden

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface IdentifyResult {
    data class Found(val plant: Plant) : IdentifyResult
    data object Unknown : IdentifyResult
    data class Failed(val message: String) : IdentifyResult
}

sealed interface TranslateResult {
    data class Done(val hindi: Map<String, String>) : TranslateResult
    data class Failed(val message: String) : TranslateResult
}

/** The plant fields that get translated, in the order they are sent. */
val TRANSLATED_FIELDS = listOf(
    "name", "sunlight", "sunlightNote", "waterNote", "fertiliser", "fertiliserNote",
    "blooming", "bloomingNote", "propagation", "propagationNote",
)

fun Plant.field(key: String): String = when (key) {
    "name" -> name
    "sunlight" -> sunlight
    "sunlightNote" -> sunlightNote
    "waterNote" -> waterNote
    "fertiliser" -> fertiliser
    "fertiliserNote" -> fertiliserNote
    "blooming" -> blooming
    "bloomingNote" -> bloomingNote
    "propagation" -> propagation
    "propagationNote" -> propagationNote
    else -> ""
}

object ClaudeApi {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    /** Region used for local plant names. Fixed for now; a region setting can come later. */
    const val REGION = "Delhi/NCR, India"

    private val IDENTIFY_PROMPT = """
        Identify the plant in this photo and give care advice for a home gardener in $REGION.
        Reply with one JSON object only, no other text, with exactly these keys:
        "identified": boolean, false if there is no plant or you cannot tell which plant it is
        "common_name": string, the English common name
        "scientific_name": string
        "local_name": string, the name most people in $REGION call this plant, in Latin letters (for example "Gudhal" for hibiscus); empty if there is no local name
        "local_name_hindi": string, that same local name in Devanagari; empty if none
        "sunlight": one of "Full sun", "Partial shade", "Indirect light", "Low light"
        "sunlight_note": string, at most 8 words
        "water_every_days": integer, typical days between waterings
        "watering_note": string, at most 8 words
        "fertiliser": string, at most 6 words, the kind of fertiliser
        "fertiliser_note": string, one sentence on how often and how
        "is_flowering": boolean
        "blooming": string, at most 6 words, season or frequency, empty if not flowering
        "blooming_note": string, one sentence, empty if not flowering
        "propagation": string, at most 5 words, the main method
        "propagation_note": string, one sentence on how to do it
    """.trimIndent()

    private const val TRANSLATE_PROMPT =
        "Translate the values of this JSON object into simple, everyday Hindi in Devanagari, " +
            "the way people speak in Delhi. Keep numbers as digits. For \"name\", use the plant's " +
            "common Hindi name if it has one. Reply with one JSON object only, no other text, " +
            "with the same keys.\n\n"

    /** Blocking; call from a background thread. */
    fun identify(apiKey: String, model: String, photo: File): IdentifyResult {
        val content = JSONArray()
            .put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", Images.base64(photo))
                )
            )
            .put(JSONObject().put("type", "text").put("text", IDENTIFY_PROMPT))
        val reply = ask(apiKey, model, content, leanMaxTokens = 900)
        reply.error?.let { return IdentifyResult.Failed(it) }
        val o = jsonIn(reply.text ?: "") ?: return IdentifyResult.Unknown
        val name = o.optString("common_name").trim()
        if (!o.optBoolean("identified", false) || name.isEmpty()) return IdentifyResult.Unknown
        val flowering = o.optBoolean("is_flowering", false)
        return IdentifyResult.Found(
            Plant(
                name = name,
                scientific = o.optString("scientific_name").trim(),
                localName = o.optString("local_name").trim(),
                localNameHi = o.optString("local_name_hindi").trim(),
                sunlight = o.optString("sunlight").trim(),
                sunlightNote = o.optString("sunlight_note").trim(),
                waterDays = o.optInt("water_every_days", 0).coerceIn(0, 365),
                waterNote = o.optString("watering_note").trim(),
                fertiliser = o.optString("fertiliser").trim(),
                fertiliserNote = o.optString("fertiliser_note").trim(),
                flowering = flowering,
                blooming = if (flowering) o.optString("blooming").trim() else "",
                bloomingNote = if (flowering) o.optString("blooming_note").trim() else "",
                propagation = o.optString("propagation").trim(),
                propagationNote = o.optString("propagation_note").trim(),
            )
        )
    }

    /** Blocking; call from a background thread. */
    fun translateToHindi(apiKey: String, model: String, plant: Plant): TranslateResult {
        val source = JSONObject()
        TRANSLATED_FIELDS.forEach { key ->
            val v = plant.field(key)
            if (v.isNotBlank()) source.put(key, v)
        }
        if (source.length() == 0) return TranslateResult.Done(mapOf("_done" to "1"))
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", TRANSLATE_PROMPT + source.toString()))
        // Devanagari takes more tokens than English, so allow more room.
        val reply = ask(apiKey, model, content, leanMaxTokens = 2000)
        reply.error?.let { return TranslateResult.Failed(it) }
        val o = jsonIn(reply.text ?: "")
            ?: return TranslateResult.Failed("The translation came back empty. Please try again.")
        val map = TRANSLATED_FIELDS
            .associateWith { o.optString(it).trim() }
            .filterValues { it.isNotEmpty() }
        if (map.isEmpty()) return TranslateResult.Failed("The translation came back empty. Please try again.")
        return TranslateResult.Done(map + ("_done" to "1"))
    }

    private class Reply(val text: String?, val error: String?)

    private fun ask(apiKey: String, model: String, content: JSONArray, leanMaxTokens: Int): Reply {
        return try {
            // Low effort with thinking off keeps token use to a minimum.
            var response = post(apiKey, body(model, content, leanMaxTokens, lean = true))
            if (response.first == 400) {
                // If the chosen model rejects those options, retry with a plain request.
                response = post(apiKey, body(model, content, 4000, lean = false))
            }
            val (code, text) = response
            if (code !in 200..299) Reply(null, errorMessage(code, text)) else Reply(textOf(text), null)
        } catch (e: java.io.IOException) {
            Reply(null, "Couldn't reach the server. Check your internet connection.")
        } catch (e: Exception) {
            Reply(null, "Something went wrong: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun body(model: String, content: JSONArray, maxTokens: Int, lean: Boolean): String {
        val o = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        if (lean) {
            o.put("thinking", JSONObject().put("type", "disabled"))
            o.put("output_config", JSONObject().put("effort", "low"))
        }
        return o.toString()
    }

    private fun post(apiKey: String, body: String): Pair<Int, String> {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(code: Int, text: String): String {
        val detail = try {
            JSONObject(text).getJSONObject("error").optString("message")
        } catch (e: Exception) {
            ""
        }
        return when (code) {
            401 -> "The API key was rejected. Check it in Settings."
            404 -> "The model name wasn't found. Check it in Settings."
            429 -> "Rate limit reached. Try again in a moment."
            else -> "Request failed ($code). $detail".trim()
        }
    }

    private fun textOf(responseText: String): String {
        val blocks = JSONObject(responseText).optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return sb.toString()
    }

    private fun jsonIn(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }
}
