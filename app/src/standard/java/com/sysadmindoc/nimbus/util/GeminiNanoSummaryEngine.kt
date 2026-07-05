package com.sysadmindoc.nimbus.util

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiNanoSummary"

/**
 * On-device weather summary generator using Gemini Nano via ML Kit GenAI Prompt API.
 *
 * Only available on supported devices. Falls back gracefully when the model is unavailable;
 * callers should always have a template fallback ready.
 */
@Singleton
class GeminiNanoSummaryEngine @Inject constructor() : SummaryEngine {
    private val model: GenerativeModel? = try {
        Generation.getClient().also { Log.d(TAG, "ML Kit GenAI Prompt client initialised") }
    } catch (e: Exception) {
        Log.w(TAG, "ML Kit GenAI Prompt client unavailable: ${e.message}")
        null
    }

    @Volatile
    private var closed = false

    @Volatile
    private var available = false

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
        if (!ensureModelReady(generativeModel)) return null

        val prompt = buildPrompt(
            currentTemp = currentTemp,
            condition = condition,
            high = high,
            low = low,
            humidity = humidity,
            windSpeed = windSpeed,
            precipChance = precipChance,
            uvIndex = uvIndex,
        )

        return try {
            val request = GenerateContentRequest.Builder(TextPart(prompt)).apply {
                temperature = 0.7f
                topK = 16
                maxOutputTokens = 128
            }.build()
            val response = generativeModel.generateContent(request)
            val text = response.candidates.firstOrNull()?.text?.trim()
            if (text.isNullOrBlank()) {
                Log.w(TAG, "Gemini Nano returned empty response")
                null
            } else {
                Log.d(TAG, "AI summary generated (${text.length} chars)")
                text
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "AI summary generation failed: ${e.message}", e)
            null
        }
    }

    override fun isAvailable(): Boolean = available && !closed

    /** Release the model resources when no longer needed. */
    override fun close() {
        if (closed) return
        closed = true
        try {
            model?.close()
        } catch (_: Exception) {}
    }

    private suspend fun ensureModelReady(generativeModel: GenerativeModel): Boolean {
        if (available) return true
        return try {
            when (val status = generativeModel.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    available = true
                    true
                }
                FeatureStatus.DOWNLOADABLE -> downloadModel(generativeModel)
                FeatureStatus.DOWNLOADING -> {
                    Log.d(TAG, "Gemini Nano model is still downloading")
                    false
                }
                FeatureStatus.UNAVAILABLE -> {
                    Log.d(TAG, "Gemini Nano model is unavailable on this device")
                    false
                }
                else -> {
                    Log.d(TAG, "Gemini Nano model returned unknown status $status")
                    false
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Gemini Nano status check failed: ${e.message}", e)
            false
        }
    }

    private suspend fun downloadModel(generativeModel: GenerativeModel): Boolean {
        var completed = false
        var failed = false
        generativeModel.download().collect { status ->
            when (status) {
                DownloadStatus.DownloadCompleted -> {
                    Log.d(TAG, "Gemini Nano model download complete")
                    completed = true
                }
                is DownloadStatus.DownloadFailed -> {
                    Log.w(TAG, "Gemini Nano model download failed: ${status.e.message}", status.e)
                    failed = true
                }
                is DownloadStatus.DownloadProgress -> {
                    Log.d(TAG, "Gemini Nano download progress: ${status.totalBytesDownloaded} bytes")
                }
                is DownloadStatus.DownloadStarted -> Log.d(TAG, "Gemini Nano model download started")
            }
        }
        if (failed || !completed) return false
        available = generativeModel.checkStatus() == FeatureStatus.AVAILABLE
        return available
    }

    companion object {
        /**
         * Build the Gemini Nano prompt from weather context. Extracted as an
         * internal helper so the prompt shape is unit-testable without mocking
         * the GenerativeModel runtime. The prompt format is what drives summary
         * quality, so locking it down with assertions is the highest-leverage
         * thing to test here.
         */
        internal fun buildPrompt(
            currentTemp: String,
            condition: String,
            high: String,
            low: String,
            humidity: Int,
            windSpeed: String,
            precipChance: Int,
            uvIndex: Double,
        ): String = buildString {
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
    }
}
