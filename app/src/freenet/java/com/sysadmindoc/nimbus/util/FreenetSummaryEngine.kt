package com.sysadmindoc.nimbus.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op [SummaryEngine] for the freenet (F-Droid) build flavor.
 * AI summary generation is not available without Google AI Core.
 */
@Singleton
class FreenetSummaryEngine @Inject constructor() : SummaryEngine {
    override suspend fun generate(
        currentTemp: String,
        condition: String,
        high: String,
        low: String,
        humidity: Int,
        windSpeed: String,
        precipChance: Int,
        uvIndex: Double,
    ): String? = null

    override fun isAvailable(): Boolean = false
    override fun close() {}
}
