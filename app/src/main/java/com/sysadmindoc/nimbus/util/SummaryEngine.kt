package com.sysadmindoc.nimbus.util

/**
 * Abstraction for on-device AI summary generation.
 *
 * - `standard` flavor: backed by [GeminiNanoSummaryEngine] (Google AI Core).
 * - `freenet` flavor: no-op implementation (AI summary unavailable).
 */
interface SummaryEngine {
    /**
     * Generate an AI-powered weather summary from the given weather parameters.
     * All temperatures are in the user's display unit (already converted).
     *
     * @return The generated summary text, or null if AI generation failed or is unavailable.
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
    ): String?

    /** Whether the engine is available on this device. */
    fun isAvailable(): Boolean

    /** Release model resources when no longer needed. */
    fun close()
}
