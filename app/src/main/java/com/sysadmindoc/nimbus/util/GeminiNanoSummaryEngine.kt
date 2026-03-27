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
) {
    private var model: GenerativeModel? = null
    private var availabilityChecked = false
    private var isAvailable = false

    /**
     * Lazily initialise the on-device model.
     * Returns null if the device doesn't support Gemini Nano.
     */
    private suspend fun getOrCreateModel(): GenerativeModel? {
        if (availabilityChecked) return model
        return try {
            availabilityChecked = true
            val generativeModel = GenerativeModel(
                generationConfig {
                    temperature = 0.7f
                    topK = 16
                    maxOutputTokens = 128
                },
            )
            model = generativeModel
            isAvailable = true
            Log.d(TAG, "Gemini Nano model initialised successfully")
            generativeModel
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano not available on this device: ${e.message}")
            isAvailable = false
            model = null
            null
        }
    }

    /**
     * Generate an AI-powered weather summary from the given weather parameters.
     * All temperatures are in the user's display unit (already converted).
     *
     * @return The generated summary text, or null if AI generation failed.
     */
    suspend fun generate(
        currentTemp: String,
        condition: String,
        high: String,
        low: String,
        humidity: Int,
        windSpeed: String,
        precipChance: Int,
        uvIndex: Double,
    ): String? {
        val generativeModel = getOrCreateModel() ?: return null

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

    /** Release the model resources when no longer needed. */
    fun close() {
        try {
            model?.close()
        } catch (_: Exception) {}
        model = null
        availabilityChecked = false
        isAvailable = false
    }
}
