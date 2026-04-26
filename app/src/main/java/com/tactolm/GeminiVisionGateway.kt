package com.tactolm

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

/** Outcome of a [GeminiVisionGateway.analyzeImage] call. */
sealed class AnalysisResult {
    /** The API returned a valid scene description. */
    data class Success(val scene: SceneResult) : AnalysisResult()
    /** Any failure — HTTP error, network error, parse error, etc. */
    object Failure : AnalysisResult()
}

// ── Gateway ───────────────────────────────────────────────────────────────────

object GeminiVisionGateway {

    private fun scaleBitmap(source: Bitmap, maxDimension: Int = 1024): Bitmap {
        val width = source.width
        val height = source.height
        val ratio = width.toFloat() / height.toFloat()

        val (newWidth, newHeight) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }
    private const val TAG = "GeminiVisionGateway"

    private val VALID_CATEGORIES = setOf(
        "PERSON", "HAZARD", "FURNITURE", "OBJECT", "FOOD", "ANIMAL", "ERROR"
    )

    private val SYSTEM_PROMPT = """
You are a vision analysis assistant embedded in a tactile navigation app for deafblind users. The user cannot see or hear. They are pointing their phone camera at their environment to understand what is around them. Your output will be converted into haptic vibration signals. This is their only sensory input from the digital world. Your job: analyze the image and return a JSON object. Return NOTHING else. No explanation. No preamble. No markdown. No code fences. Only raw JSON. Identify the most important objects or elements visible in the image. For each item classify it into exactly one of these six categories: PERSON meaning any human being face body crowd child or adult. HAZARD meaning stairs fire sharp objects wet surfaces traffic obstacles at head or foot level or anything dangerous. FURNITURE meaning chairs tables doors walls beds sofas shelves or structural elements. OBJECT meaning phones bottles books bags remotes tools appliances or any interactable item. FOOD meaning food drinks cups plates bowls fruit or any consumable. ANIMAL meaning dogs cats birds or any living non-human creature. Priority rules: Always report HAZARD first if one exists regardless of how minor it seems. Always report PERSON second if one exists. Report remaining categories in order of how prominent they are in the scene. If the same category appears multiple times list it only once. Return a maximum of 5 items and a minimum of 1 item. If the image is too dark blurry or unclear to analyze return a single item with category ERROR. Return this exact JSON format with no deviations: {scene_summary: one sentence maximum 12 words describing the overall environment and spatial layout, items: [{label: specific object name 1 to 3 words, category: one of the six category names exactly as written above, confidence: HIGH or MEDIUM or LOW, priority: integer 1 through 5 where 1 is most important}]}
    """.trimIndent()


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
     * Makes a single call to Gemini and returns the result.
     *
     * Returns [AnalysisResult.Success] when the API responds with valid JSON.
     * Returns [AnalysisResult.Failure] for any other outcome.
     * No retries are performed.
     */
    suspend fun analyzeImage(bitmap: Bitmap, apiKey: String): AnalysisResult =
        withContext(Dispatchers.IO) {
            val scaledBitmap = scaleBitmap(bitmap)
            try {
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        maxOutputTokens = 1024
                        temperature = 0.1f // Lowered from 0.2 to make it even more literal
                        topP = 0.95f       // Focuses the output on the most likely tokens
                        topK = 40          // Limits the "vocabulary" per step
                        responseMimeType = "application/json"
                    },
                    systemInstruction = content {
                        text(SYSTEM_PROMPT)
                    }
                )

                Log.d("TactoLM_Gemini", "=== GEMINI API CALL STARTED ===")
                Log.d("TactoLM_Gemini", "Timestamp: " + System.currentTimeMillis())
                Log.d("TactoLM_Gemini", "Scaled dimensions: ${scaledBitmap.width}x${scaledBitmap.height}")
                Log.d("TactoLM_Gemini", "Model: " + model.modelName)
                Log.d("TactoLM_Gemini", "Timestamp: " + System.currentTimeMillis())
                Log.d("TactoLM_Gemini", "Scaled size: ${scaledBitmap.byteCount / 1024} KB")

                val response = model.generateContent(
                    content {
                        image(scaledBitmap)
                        text("Analyze this image for the deafblind user.")
                    }
                )

                Log.d("TactoLM_Gemini", "=== GEMINI RESPONSE RECEIVED ===")
                Log.d("TactoLM_Gemini", "response.text is null: " + (response.text == null))
                Log.d("TactoLM_Gemini", "Raw response: " + response.text)
                Log.d("TactoLM_Gemini", "Raw response text: " + response.text)

                val rawText = response.text ?: run {
                    Log.e("TactoLM_Gemini", "ERROR: response.text is null. Returning failure.")
                    Log.e("TactoLM_Gemini", "ERROR: response.text is null")
                    return@withContext AnalysisResult.Failure
                }

                val cleanJson = rawText.trim()
                    .replace(Regex("^```json", RegexOption.MULTILINE), "")
                    .replace(Regex("^```", RegexOption.MULTILINE), "")
                    .replace(Regex("```$", RegexOption.MULTILINE), "")
                    .trim()

                if (!cleanJson.endsWith("}")) {
                    Log.e("TactoLM_Gemini", "JSON appears truncated. Attempting manual closure.")
                    // You could potentially append "]}" here if it's consistently cutting off the items list
                }

                Log.d("TactoLM_Gemini", "Cleaned JSON: " + cleanJson)

                val scene = parseSceneJson(cleanJson)
                if (scene == null) {
                    Log.e("TactoLM_Gemini", "ERROR: parseSceneJson returned null.")
                    Log.e("TactoLM_Gemini", "Failed JSON string was: " + cleanJson)
                    Log.e("TactoLM_Gemini", "ERROR: JSON parsing failed on: " + cleanJson)
                    return@withContext AnalysisResult.Failure
                }

                Log.d("TactoLM_Gemini", "JSON parse SUCCESS")
                Log.d("TactoLM_Gemini", "Parse SUCCESS")
                Log.d("TactoLM_Gemini", "Scene summary: " + scene.summary)
                Log.d("TactoLM_Gemini", "Item count: " + scene.items.size)
                for (item in scene.items) {
                    Log.d("TactoLM_Gemini", "Parsed item -> label: " + item.label +
                        " | category: " + item.category +
                        " | confidence: " + item.confidence +
                        " | priority: " + item.priority)
                    Log.d("TactoLM_Gemini", "Item -> label: " + item.label +
                        " | category: " + item.category +
                        " | confidence: " + item.confidence +
                        " | priority: " + item.priority)
                }

                AnalysisResult.Success(scene)

            } catch (e: Exception) {
                Log.e("TactoLM_Gemini", "=== EXCEPTION IN GEMINI CALL ===")
                Log.e("TactoLM_Gemini", "Exception type: " + e.javaClass.simpleName)
                Log.e("TactoLM_Gemini", "Exception message: " + e.message)
                Log.e("TactoLM_Gemini", "Full stack trace: " + Log.getStackTraceString(e))
                Log.e("TactoLM_Gemini", "EXCEPTION during API call: " + e.javaClass.simpleName)
                Log.e("TactoLM_Gemini", "Exception message: " + e.message)
                Log.e("TactoLM_Gemini", "Stack trace: " + Log.getStackTraceString(e))
                AnalysisResult.Failure
            }
        }
}
