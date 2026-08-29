package com.sysadmindoc.nimbus.util

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateTypedContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
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

        // Structured output is what makes the summary checkable. Without it the
        // model returns prose whose numbers cannot be traced back to the
        // forecast, so the caller's template summary is the safer answer.
        if (!supportsStructuredOutput(generativeModel)) return null
        // The system instruction is what keeps digits out of the prose. A
        // device that ignores it would return exactly the free text this
        // feature exists to stop, so fall back rather than trust it.
        if (!supportsSystemPrompt(generativeModel)) return null

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
        val facts = SummaryFacts.from(currentTemp = currentTemp, precipChance = precipChance)

        return try {
            val contentRequest = GenerateContentRequest.Builder(
                SystemInstruction(SYSTEM_INSTRUCTION),
                TextPart(prompt),
            ).apply {
                // Lower than the old 0.7: the model is filling a schema from
                // supplied numbers, not writing freely, and every invented
                // number costs a rejection and a fallback.
                temperature = 0.3f
                topK = 16
                maxOutputTokens = 192
            }.build()
            val request = GenerateTypedContentRequest
                .Builder(contentRequest, WeatherSummaryDraft::class)
                .build()
            val draft = generativeModel.generateContent(request)
                .candidates
                .firstOrNull()
                ?.response
            if (draft == null) {
                Log.w(TAG, "Gemini Nano returned no typed candidate")
                return null
            }
            WeatherSummaryValidator.validate(draft, facts).fold(
                onSuccess = { summary ->
                    Log.d(TAG, "AI summary accepted (${summary.length} chars)")
                    summary
                },
                onFailure = { rejection ->
                    // Falling back to the template beats showing a number the
                    // forecast never contained.
                    Log.w(TAG, "AI summary rejected: ${rejection.message}")
                    null
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "AI summary generation failed: ${e.message}", e)
            null
        }
    }

    private suspend fun supportsStructuredOutput(generativeModel: GenerativeModel): Boolean =
        capability("Structured output") { generativeModel.isStructuredOutputFeatureAvailable() }

    private suspend fun supportsSystemPrompt(generativeModel: GenerativeModel): Boolean =
        capability("System prompt") { generativeModel.isSystemPromptAvailable() }

    private suspend fun capability(name: String, check: suspend () -> Boolean): Boolean =
        try {
            check().also { if (!it) Log.d(TAG, "$name unavailable; using the template summary") }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "$name capability check failed: ${e.message}", e)
            false
        }

    override fun isAvailable(): Boolean = available && !closed

    /** Release the model resources when no longer needed. */
    override fun close() {
        if (closed) return
        closed = true
        try {
            model?.close()
        } catch (ignored: Exception) {}
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
         * Keeps the model inside the numbers it was handed. Every invented
         * figure costs a validator rejection and a fall back to the template,
         * so the instruction is worth more than a longer prompt.
         */
        internal const val SYSTEM_INSTRUCTION =
            "You write short weather summaries. Describe the weather in words only: " +
                "never write a digit or a number word in the headline or detail. " +
                "Put the current temperature and the rain chance from the request into " +
                "the numeric fields exactly as given, without rounding or estimating."

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
