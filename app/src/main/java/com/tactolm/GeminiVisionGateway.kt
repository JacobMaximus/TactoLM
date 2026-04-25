package com.tactolm

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ── Data models ───────────────────────────────────────────────────────────────

data class SceneItem(
    val label: String,
    val category: String,
    val confidence: String,
    val priority: Int
)

data class SceneResult(
    val summary: String,
    val items: List<SceneItem>
)

// ── Gateway ───────────────────────────────────────────────────────────────────

object GeminiVisionGateway {

    private const val TAG = "GeminiVisionGateway"

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val VALID_CATEGORIES = setOf(
        "PERSON", "HAZARD", "FURNITURE", "OBJECT", "FOOD", "ANIMAL", "ERROR"
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SYSTEM_PROMPT = """
You are a vision analysis assistant embedded in a tactile navigation app for deafblind users. The user cannot see or hear. They are pointing their phone camera at their environment to understand what is around them. Your output will be converted into haptic vibration signals. This is their only sensory input from the digital world. Your job: analyze the image and return a JSON object. Return NOTHING else. No explanation. No preamble. No markdown. No code fences. Only raw JSON. Identify the most important objects or elements visible in the image. For each item classify it into exactly one of these six categories: PERSON meaning any human being face body crowd child or adult. HAZARD meaning stairs fire sharp objects wet surfaces traffic obstacles at head or foot level or anything dangerous. FURNITURE meaning chairs tables doors walls beds sofas shelves or structural elements. OBJECT meaning phones bottles books bags remotes tools appliances or any interactable item. FOOD meaning food drinks cups plates bowls fruit or any consumable. ANIMAL meaning dogs cats birds or any living non-human creature. Priority rules: Always report HAZARD first if one exists regardless of how minor it seems. Always report PERSON second if one exists. Report remaining categories in order of how prominent they are in the scene. If the same category appears multiple times list it only once. Return a maximum of 5 items and a minimum of 1 item. If the image is too dark blurry or unclear to analyze return a single item with category ERROR. Return this exact JSON format with no deviations: {scene_summary: one sentence maximum 12 words describing the overall environment and spatial layout, items: [{label: specific object name 1 to 3 words, category: one of the six category names exactly as written above, confidence: HIGH or MEDIUM or LOW, priority: integer 1 through 5 where 1 is most important}]}
    """.trimIndent()

    /**
     * Scale [bitmap] so its longest side does not exceed [maxSide] pixels,
     * then compress as JPEG at [quality]%, then Base64-encode.
     * Returns the encoded string.
     */
    private fun encodeImage(bitmap: Bitmap, maxSide: Int = 768, quality: Int = 40): String {
        val w = bitmap.width
        val h = bitmap.height
        val scaled: Bitmap = if (w <= maxSide && h <= maxSide) {
            bitmap
        } else {
            val ratio = maxSide.toFloat() / maxOf(w, h)
            val nw = (w * ratio).toInt().coerceAtLeast(1)
            val nh = (h * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Build the JSON request body as a String.
     */
    private fun buildRequestBody(base64Image: String): String {
        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", SYSTEM_PROMPT)
                })
            })
        }

        val inlineData = JSONObject().apply {
            put("mime_type", "image/jpeg")
            put("data", base64Image)
        }
        val imagePart = JSONObject().apply {
            put("inline_data", inlineData)
        }
        val textPart = JSONObject().apply {
            put("text", "Analyze this image for the deafblind user.")
        }

        val contents = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(imagePart)
                    put(textPart)
                })
            })
        }

        val generationConfig = JSONObject().apply {
            put("maxOutputTokens", 300)
            put("temperature", 0.2)
        }

        return JSONObject().apply {
            put("system_instruction", systemInstruction)
            put("contents", contents)
            put("generationConfig", generationConfig)
        }.toString()
    }

    /**
     * Parse a raw Gemini response string and extract the embedded JSON text.
     */
    private fun extractTextFromResponse(responseBody: String): String? {
        return try {
            val root = JSONObject(responseBody)
            root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from Gemini response", e)
            null
        }
    }

    /**
     * Strip optional ```json … ``` markdown fences and parse the JSON payload.
     */
    private fun parseSceneJson(raw: String): SceneResult? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val root = JSONObject(cleaned)
            val summary = root.optString("scene_summary", "")
            val itemsArray: JSONArray = root.optJSONArray("items") ?: JSONArray()

            val items = mutableListOf<SceneItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                val cat = obj.optString("category", "").uppercase()
                if (cat !in VALID_CATEGORIES) {
                    Log.d(TAG, "Skipping unknown category: $cat")
                    continue
                }
                items.add(
                    SceneItem(
                        label      = obj.optString("label", "Unknown"),
                        category   = cat,
                        confidence = obj.optString("confidence", "LOW").uppercase(),
                        priority   = obj.optInt("priority", 99)
                    )
                )
            }
            SceneResult(summary, items)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}")
            null
        }
    }

    /**
     * POST to Gemini with exponential backoff.
     *
     * Retry schedule:
     *   Attempt 1 — immediately
     *   Attempt 2 — after 3 000 ms (on 429 / 5xx)
     *   Attempt 3 — after 6 000 ms
     *
     * Returns [SceneResult] on success, null if all retries exhausted.
     */
    suspend fun analyzeImage(bitmap: Bitmap, apiKey: String): SceneResult? =
        withContext(Dispatchers.IO) {
            val base64 = encodeImage(bitmap)
            val bodyString = buildRequestBody(base64)
            val mediaType = "application/json".toMediaType()

            val delays = longArrayOf(0L, 3000L, 6000L)

            for ((attempt, delayMs) in delays.withIndex()) {
                if (delayMs > 0L) {
                    Log.d(TAG, "Waiting ${delayMs}ms before attempt ${attempt + 1}")
                    delay(delayMs)
                }

                try {
                    val request = Request.Builder()
                        .url("$ENDPOINT?key=$apiKey")
                        .post(bodyString.toRequestBody(mediaType))
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    val code = response.code
                    Log.d(TAG, "Attempt ${attempt + 1}: HTTP $code")

                    if (code == 200) {
                        val body = response.body?.string()
                        if (body == null) {
                            Log.e(TAG, "Empty response body")
                            continue
                        }
                        val rawText = extractTextFromResponse(body)
                            ?: continue
                        val result = parseSceneJson(rawText)
                        if (result != null) {
                            return@withContext result
                        }
                        // Parse failed — don't retry for a parse error; stop
                        return@withContext null
                    }

                    // 429 or 5xx → retry
                    if (code == 429 || code in 500..599) {
                        Log.w(TAG, "Retriable HTTP $code on attempt ${attempt + 1}")
                        // loop continues to next delay
                    } else {
                        // Non-retriable client error
                        Log.e(TAG, "Non-retriable HTTP $code — aborting")
                        return@withContext null
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Network error on attempt ${attempt + 1}: ${e.message}")
                    // treat as retriable; continue
                }
            }

            Log.e(TAG, "All retry attempts exhausted")
            null
        }
}
