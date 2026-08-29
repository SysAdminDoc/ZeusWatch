package com.sysadmindoc.nimbus.data.api

import kotlinx.coroutines.test.runTest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `models=` value is the entire difference between one Open-Meteo model
 * wrapper and another, and a wrong one does not fail: the API answers 200 with
 * a full-length array of nulls, which deserializes cleanly and renders as
 * zero degrees. So the value actually put on the wire is asserted here rather
 * than trusted to a mocked adapter.
 */
class OpenMeteoModelQueryTest {

    @Test
    fun `each model wrapper sends its own models parameter`() = runTest {
        val requests = mutableListOf<Request>()
        val api = api(requests)

        api.getAifsForecast(40.71, -74.01)
        api.getUkmoForecast(40.71, -74.01)
        api.getDmiForecast(40.71, -74.01)

        val models = requests.map { it.url.queryParameter("models") }
        assertEquals(listOf("ecmwf_aifs025_single", "ukmo_seamless", "dmi_seamless"), models)
    }

    @Test
    fun `the AIFS wrapper uses the single-run id that actually carries data`() = runTest {
        // ecmwf_aifs025 resolves and returns 200, but every hourly value is
        // null. Only the _single variant is populated on the forecast API.
        assertEquals("ecmwf_aifs025_single", OpenMeteoApi.AIFS_MODEL)

        val requests = mutableListOf<Request>()
        api(requests).getAifsForecast(40.71, -74.01)

        assertEquals(OpenMeteoApi.AIFS_MODEL, requests.single().url.queryParameter("models"))
    }

    @Test
    fun `the live contract check uses the same model id as the app`() {
        // The id is spelled out again in the checker; without this they can
        // drift and the check would keep passing against a model the app no
        // longer requests.
        val checker = repositoryRoot().resolve("tools/check_provider_contracts.py").toFile().readText()

        assertTrue(
            "check_provider_contracts.py does not reference ${OpenMeteoApi.AIFS_MODEL}",
            checker.contains("\"${OpenMeteoApi.AIFS_MODEL}\""),
        )
    }

    private fun api(requests: MutableList<Request>): OpenMeteoApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(EMPTY_BODY.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        return Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    private fun repositoryRoot(): Path {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isDirectory(candidate.resolve("app/src/main"))) {
            candidate = candidate.parent ?: error("Could not locate repository root")
        }
        return candidate
    }

    private companion object {
        const val EMPTY_BODY = """{"latitude":40.71,"longitude":-74.01}"""
    }
}
