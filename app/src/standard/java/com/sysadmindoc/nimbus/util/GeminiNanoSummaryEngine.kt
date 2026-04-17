package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.GenerateContentResponse
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiNanoSummary"

/**
 * On-device AI weather summary generator using Gemini Nano via ML Kit GenAI (AI Core).
 *
 * Only available on supported devices (Pixel 8+, select Samsung). Falls back gracefully
 * when the model is unavailable — callers should always have a template fallback ready.
 */
@Singleton
class GeminiNanoSummaryEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SummaryEngine {
    // Eagerly attempt to construct the model so `isAvailable()` can give a truthful
    // answer before any `generate()` call. Previously `isAvailable()` returned the
    // default `false` until `getOrCreateModel()` was invoked inside `generate()` —
    // but `generate()` was never called because `MainViewModel` gates on
    // `isAvailable()`. That chicken-and-egg deadlock meant AI summaries (the app's
    // default `SummaryStyle`) never ran on real devices.
    private val model: GenerativeModel? = try {
        GenerativeModel(
            generationConfig {
                temperature = 0.7f
                topK = 16
                maxOutputTokens = 128
            },
        ).also { Log.d(TAG, "Gemini Nano model initialised successfully") }
    } catch (e: Exception) {
        Log.w(TAG, "Gemini Nano not available on this device: ${e.message}")
        null
    }

    private val _isAvailable: Boolean = model != null

    private var closed = false

    /**
     * Generate an AI-powered weather summary from the given weather parameters.
     * All temperatures are in the user's display unit (already converted).
     *
     * @return The generated summary text, or null if AI generation failed.
     */
    override suspend fun generate(
        currentTemp: String,
        condition: String,
        high: String,
        low: String,
        humidity: Int,
        windSpeed: String,
        precipChance: Int,
        uvIndex: Double,
    ): String? {
        if (closed) return null
        val generativeModel = model ?: return null

        val prompt = buildString {
            append("Write a brief, friendly 1-2 sentence weather summary for: ")
            append("Currently $currentTemp, $condition. ")
            append("High $high, low $low. ")
            if (precipChance > 0) {
                append("${precipChance}% chance of rain. ")
            }
            append("Wind $windSpeed. ")
            append("UV index ${uvIndex.toInt()}. ")
            append("Humidity $humidity%.")
        }

        return try {
            val response: GenerateContentResponse = generativeModel.generateContent(prompt)
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                Log.w(TAG, "Gemini Nano returned empty response")
                null
            } else {
                Log.d(TAG, "AI summary generated (${text.length} chars)")
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI summary generation failed: ${e.message}", e)
            null
        }
    }

    override fun isAvailable(): Boolean = _isAvailable && !closed

    /** Release the model resources when no longer needed. */
    override fun close() {
        if (closed) return
        closed = true
        try {
            model?.close()
        } catch (_: Exception) {}
    }
}
