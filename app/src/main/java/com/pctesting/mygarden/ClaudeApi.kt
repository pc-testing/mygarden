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

object ClaudeApi {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    private val PROMPT = """
        Identify the plant in this photo and give care advice for a home gardener.
        Reply with one JSON object only, no other text, with exactly these keys:
        "identified": boolean, false if there is no plant or you cannot tell which plant it is
        "common_name": string
        "scientific_name": string
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

    /** Blocking; call from a background thread. */
    fun identify(apiKey: String, model: String, photo: File): IdentifyResult {
        return try {
            val image = Images.base64(photo)
            // Low effort with thinking off keeps token use to a minimum.
            var response = post(apiKey, body(model, image, lean = true))
            if (response.first == 400) {
                // If the chosen model rejects those options, retry with a plain request.
                response = post(apiKey, body(model, image, lean = false))
            }
            val (code, text) = response
            if (code !in 200..299) return IdentifyResult.Failed(errorMessage(code, text))
            parse(text)
        } catch (e: java.io.IOException) {
            IdentifyResult.Failed("Couldn't reach the server. Check your internet connection.")
        } catch (e: Exception) {
            IdentifyResult.Failed("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun body(model: String, imageBase64: String, lean: Boolean): String {
        val content = JSONArray()
            .put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", imageBase64)
                )
            )
            .put(JSONObject().put("type", "text").put("text", PROMPT))
        val o = JSONObject()
            .put("model", model)
            .put("max_tokens", if (lean) 700 else 4000)
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

    private fun parse(responseText: String): IdentifyResult {
        val blocks = JSONObject(responseText).optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        val text = sb.toString()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return IdentifyResult.Unknown
        val o = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            return IdentifyResult.Unknown
        }
        val name = o.optString("common_name").trim()
        if (!o.optBoolean("identified", false) || name.isEmpty()) return IdentifyResult.Unknown
        val flowering = o.optBoolean("is_flowering", false)
        return IdentifyResult.Found(
            Plant(
                name = name,
                scientific = o.optString("scientific_name").trim(),
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
}
