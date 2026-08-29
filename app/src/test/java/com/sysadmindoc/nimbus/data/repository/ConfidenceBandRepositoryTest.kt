package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoEnsembleApi
import com.sysadmindoc.nimbus.data.model.EnsembleResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ensemble behind a confidence band is a user choice, so the `models=`
 * value the app sends and the model it reports back both have to follow that
 * choice. A band drawn by one ensemble and labelled as another is worse than
 * no band at all.
 */
class ConfidenceBandRepositoryTest {

    @Test
    fun `each model sends its own Open-Meteo identifier`() = runTest {
        // These identifiers were verified against the live ensemble API; a typo
        // here fails the request rather than silently falling back.
        val expected = mapOf(
            EnsembleModel.ICON to "icon_seamless",
            EnsembleModel.WEATHERNEXT_2 to "google_weathernext2_ensemble",
            EnsembleModel.AIFS_ENS to "ecmwf_aifs025_ensemble",
        )

        expected.forEach { (model, apiId) ->
            val api = RecordingEnsembleApi()
            ConfidenceBandRepository(api).getConfidenceBands(47.6, -122.3, model)

            assertEquals(apiId, api.lastModels)
            assertEquals(apiId, model.apiId)
        }
    }

    @Test
    fun `the parsed band reports the model that produced it`() = runTest {
        val api = RecordingEnsembleApi()

        val data = ConfidenceBandRepository(api)
            .getConfidenceBands(47.6, -122.3, EnsembleModel.AIFS_ENS)
            .getOrThrow()

        assertEquals(EnsembleModel.AIFS_ENS, data.model)
        assertEquals(2, data.entries.size)
    }

    @Test
    fun `an empty payload still carries the model`() = runTest {
        val api = RecordingEnsembleApi(body = """{"time":[]}""")

        val data = ConfidenceBandRepository(api)
            .getConfidenceBands(47.6, -122.3, EnsembleModel.WEATHERNEXT_2)
            .getOrThrow()

        assertEquals(EnsembleModel.WEATHERNEXT_2, data.model)
        assertTrue(data.entries.isEmpty())
    }

    @Test
    fun `ICON stays the default when no model is given`() = runTest {
        val api = RecordingEnsembleApi()

        ConfidenceBandRepository(api).getConfidenceBands(47.6, -122.3)

        assertEquals(EnsembleModel.ICON.apiId, api.lastModels)
    }

    private class RecordingEnsembleApi(
        private val body: String = DEFAULT_BODY,
    ) : OpenMeteoEnsembleApi {
        var lastModels: String? = null
            private set

        override suspend fun getEnsemble(
            latitude: Double,
            longitude: Double,
            hourly: String,
            models: String,
            temperatureUnit: String,
            timezone: String,
            forecastDays: Int,
        ): EnsembleResponse {
            lastModels = models
            return EnsembleResponse(
                latitude = latitude,
                longitude = longitude,
                hourly = Json.decodeFromString<JsonObject>(body),
            )
        }

        companion object {
            const val DEFAULT_BODY = """
                {
                  "time": ["2026-05-17T10:00", "2026-05-17T11:00"],
                  "temperature_2m_member01": [10.0, 12.0],
                  "temperature_2m_member02": [11.0, 13.0],
                  "temperature_2m_member03": [12.0, 14.0]
                }
            """
        }
    }
}
