package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.FmiForecastApi
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FmiForecastAdapterTest {

    @Test
    fun mapsWfsTimeValuePairsIntoWeatherData() {
        val adapter = FmiForecastAdapter(mockk())

        val data = adapter.mapToWeatherData(
            xml = sampleFmiXml(),
            requestedLat = 60.1699,
            requestedLon = 24.9384,
            locationName = null,
            requestedZone = null,
            now = LocalDateTime.of(2026, 7, 2, 15, 5),
        )

        assertEquals("Central Helsinki", data.location.name)
        assertEquals("Helsinki", data.location.region)
        assertEquals("Finland", data.location.country)
        assertEquals("Europe/Helsinki", data.location.timeZone)
        assertEquals(LocalDateTime.of(2026, 7, 2, 15, 0), data.current.observationTime)
        assertEquals(18.0, data.current.temperature, 0.001)
        assertEquals(80, data.current.humidity)
        assertEquals(WeatherCode.RAIN_SLIGHT, data.current.weatherCode)
        assertEquals(18.0, data.current.windSpeed, 0.001)
        assertEquals(28.8, data.current.windGusts ?: -1.0, 0.001)
        assertEquals(180, data.current.windDirection)
        assertEquals(1009.3, data.current.pressure, 0.001)
        assertEquals(15000.0, data.current.visibility ?: -1.0, 0.001)
        assertEquals(12.0, data.current.dewPoint ?: -1.0, 0.001)
        assertEquals(1.2, data.current.precipitation, 0.001)
        assertEquals(19.0, data.current.dailyHigh, 0.001)
        assertEquals(18.0, data.current.dailyLow, 0.001)

        assertEquals(2, data.hourly.size)
        assertEquals(LocalDate.of(2026, 7, 2), data.daily.first().date)
        assertEquals(1.2, data.daily.first().precipitationSum ?: -1.0, 0.001)
    }

    @Test
    fun getWeatherFetchesHarmonieXmlAndUsesRequestedZone() = runTest {
        val api = mockk<FmiForecastApi>()
        val adapter = FmiForecastAdapter(api)
        coEvery {
            api.getHarmonieForecast(any(), any(), any(), any(), any(), any(), any())
        } returns sampleFmiXml().toResponseBody()

        val data = adapter.getWeather(
            latitude = 60.1699,
            longitude = 24.9384,
            locationName = "Helsinki",
            locationZone = ZoneId.of("Europe/Helsinki"),
        ).getOrThrow()

        assertEquals("Helsinki", data.location.name)
        assertEquals("Europe/Helsinki", data.location.timeZone)
        coVerify(exactly = 1) {
            api.getHarmonieForecast(
                service = "WFS",
                version = "2.0.0",
                request = "getFeature",
                storedQueryId = FmiForecastApi.HARMONIE_POINT_TIME_VALUE_QUERY,
                latLon = "60.169900,24.938400",
                parameters = FmiForecastApi.FORECAST_PARAMETERS,
                timestepMinutes = 60,
            )
        }
    }

    @Test
    fun getWeatherRejectsCoordinatesOutsideHarmonieCoverageBeforeNetwork() = runTest {
        val api = mockk<FmiForecastApi>()
        val result = FmiForecastAdapter(api).getWeather(40.7128, -74.0060, "New York")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) {
            api.getHarmonieForecast(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun weatherSymbol3MappingCoversCoreFmiConditions() {
        assertEquals(WeatherCode.CLEAR_SKY, WeatherCode.fromCode(weatherSymbol3ToWmo(1)))
        assertEquals(WeatherCode.RAIN_SHOWERS_MODERATE, WeatherCode.fromCode(weatherSymbol3ToWmo(22)))
        assertEquals(WeatherCode.RAIN_HEAVY, WeatherCode.fromCode(weatherSymbol3ToWmo(33)))
        assertEquals(WeatherCode.SNOW_HEAVY, WeatherCode.fromCode(weatherSymbol3ToWmo(53)))
        assertEquals(WeatherCode.THUNDERSTORM_HAIL_SLIGHT, WeatherCode.fromCode(weatherSymbol3ToWmo(62)))
    }

    private fun sampleFmiXml(): String {
        val values = mapOf(
            "Temperature" to listOf(18.0, 19.0),
            "Humidity" to listOf(80.0, 76.0),
            "Pressure" to listOf(1009.3, 1008.8),
            "WindSpeedMS" to listOf(5.0, 6.0),
            "WindDirection" to listOf(180.0, 190.0),
            "WindGust" to listOf(8.0, 9.0),
            "TotalCloudCover" to listOf(90.0, 70.0),
            "Precipitation1h" to listOf(1.2, 0.0),
            "Visibility" to listOf(15000.0, 14000.0),
            "DewPoint" to listOf(12.0, 13.0),
            "WeatherSymbol3" to listOf(31.0, 2.0),
        )
        return """
            <wfs:FeatureCollection
                xmlns:wfs="http://www.opengis.net/wfs/2.0"
                xmlns:omso="http://inspire.ec.europa.eu/schemas/omso/3.0"
                xmlns:om="http://www.opengis.net/om/2.0"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:gml="http://www.opengis.net/gml/3.2"
                xmlns:sam="http://www.opengis.net/sampling/2.0"
                xmlns:sams="http://www.opengis.net/samplingSpatial/2.0"
                xmlns:target="http://xml.fmi.fi/namespace/om/atmosphericfeatures/1.1"
                xmlns:wml2="http://www.opengis.net/waterml/2.0">
                ${values.entries.joinToString("\n") { (parameter, data) -> observation(parameter, data) }}
            </wfs:FeatureCollection>
        """.trimIndent()
    }

    private fun observation(parameter: String, values: List<Double>): String {
        val times = listOf("2026-07-02T12:00:00Z", "2026-07-02T13:00:00Z")
        return """
            <wfs:member>
                <omso:PointTimeSeriesObservation>
                    <om:observedProperty xlink:href="https://opendata.fmi.fi/meta?observableProperty=forecast&amp;param=$parameter&amp;language=eng"/>
                    <om:featureOfInterest>
                        <sams:SF_SpatialSamplingFeature>
                            <sam:sampledFeature>
                                <target:LocationCollection>
                                    <target:member>
                                        <target:Location>
                                            <gml:name codeSpace="http://xml.fmi.fi/namespace/locationcode/name">Central Helsinki</gml:name>
                                            <target:country>Finland</target:country>
                                            <target:timezone>Europe/Helsinki</target:timezone>
                                            <target:region>Helsinki</target:region>
                                        </target:Location>
                                    </target:member>
                                </target:LocationCollection>
                            </sam:sampledFeature>
                        </sams:SF_SpatialSamplingFeature>
                    </om:featureOfInterest>
                    <om:result>
                        <wml2:MeasurementTimeseries>
                            ${times.zip(values).joinToString("\n") { (time, value) -> point(time, value) }}
                        </wml2:MeasurementTimeseries>
                    </om:result>
                </omso:PointTimeSeriesObservation>
            </wfs:member>
        """.trimIndent()
    }

    private fun point(time: String, value: Double): String = """
        <wml2:point>
            <wml2:MeasurementTVP>
                <wml2:time>$time</wml2:time>
                <wml2:value>$value</wml2:value>
            </wml2:MeasurementTVP>
        </wml2:point>
    """.trimIndent()
}
