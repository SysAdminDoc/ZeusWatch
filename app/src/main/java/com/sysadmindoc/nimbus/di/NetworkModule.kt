package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sysadmindoc.nimbus.data.api.AirQualityApi
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.ApiCertificatePins
import com.sysadmindoc.nimbus.data.api.BrightSkyApi
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaAlertAdapter
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaAlertApi
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaForecastApi
import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.JmaAlertAdapter
import com.sysadmindoc.nimbus.data.api.JmaAlertApi
import com.sysadmindoc.nimbus.data.api.MetNorwayApi
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmApi
import com.sysadmindoc.nimbus.data.api.NwsAlertAdapter
import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoArchiveApi
import com.sysadmindoc.nimbus.data.api.OpenWeatherMapApi
import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import com.sysadmindoc.nimbus.data.api.RateLimitInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Redacts OWM/Pirate-Weather API keys from logcat output so debug builds
 * don't leak user-supplied credentials in screen recordings or bug reports.
 *
 * Lookbehind with alternation has historically been brittle in the JVM
 * regex engine, so we capture the leading `?name=` / `&name=` as group 1
 * and rewrite the value with a back-reference.
 */
private val REDACT_REGEX = Regex(
    "([?&](?:appid|apikey|api_key|key)=)[^&\\s]+",
    RegexOption.IGNORE_CASE,
)

private object ApiKeyRedactingLogger : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        android.util.Log.d("OkHttp", REDACT_REGEX.replace(message, "$1***"))
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Retry interceptor: retries on IOException or transient 5xx up to 2 times
     * with exponential backoff (1s, 2s). Runs on OkHttp's dispatcher thread —
     * `Thread.sleep` is acceptable there (it is not a coroutine context), but
     * we always pipe the backoff through an interruptible wait so a cancelled
     * request tears down promptly instead of pinning the thread.
     *
     * 429 rate-limit responses are *not* retried here; [RateLimitInterceptor]
     * handles those with a single `Retry-After`-honoring retry.
     */
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var lastException: IOException? = null
        for (attempt in 0..2) {
            try {
                val response = chain.proceed(request)
                if (response.code in 500..599 && attempt < 2) {
                    response.close()
                } else {
                    return@Interceptor response
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt >= 2) throw e
            }
            try {
                Thread.sleep(1_000L * (1 shl attempt)) // 1s, 2s
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Retry interrupted", interrupted)
            }
        }
        throw lastException ?: IOException("Request failed after retries")
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(retryInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    // OkHttp 4.x's HttpLoggingInterceptor doesn't support query-param
                    // redaction, so we route log output through a custom Logger that
                    // scrubs user-supplied API keys (OWM ?appid=, Pirate Weather
                    // ?apikey=) before they hit logcat.
                    addInterceptor(
                        HttpLoggingInterceptor(ApiKeyRedactingLogger).apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        }
                    )
                }
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ZeusWatch/${BuildConfig.VERSION_NAME} (Android; Open-Source)")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @Named("weather")
    fun provideWeatherRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("geocoding")
    fun provideGeocodingRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(GeocodingApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApi(@Named("weather") retrofit: Retrofit): OpenMeteoApi {
        return retrofit.create(OpenMeteoApi::class.java)
    }

    @Provides
    @Singleton
    @Named("archive")
    fun provideArchiveRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OpenMeteoArchiveApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenMeteoArchiveApi(@Named("archive") retrofit: Retrofit): OpenMeteoArchiveApi {
        return retrofit.create(OpenMeteoArchiveApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeocodingApi(@Named("geocoding") retrofit: Retrofit): GeocodingApi {
        return retrofit.create(GeocodingApi::class.java)
    }

    @Provides
    @Singleton
    @Named("rainviewer")
    fun provideRainViewerRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(RainViewerApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideRainViewerApi(@Named("rainviewer") retrofit: Retrofit): RainViewerApi {
        return retrofit.create(RainViewerApi::class.java)
    }

    @Provides
    @Singleton
    @Named("nws")
    fun provideNwsRetrofit(client: OkHttpClient): Retrofit {
        // NWS requires Accept header for GeoJSON responses
        val nwsClient = client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/geo+json")
                    .header("User-Agent", "ZeusWatch/${BuildConfig.VERSION_NAME} (Android; Open-Source; contact@sysadmindoc.com)")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(NwsAlertApi.BASE_URL)
            .client(nwsClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideNwsAlertApi(@Named("nws") retrofit: Retrofit): NwsAlertApi {
        return retrofit.create(NwsAlertApi::class.java)
    }

    @Provides
    @Singleton
    @Named("airquality")
    fun provideAirQualityRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AirQualityApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAirQualityApi(@Named("airquality") retrofit: Retrofit): AirQualityApi {
        return retrofit.create(AirQualityApi::class.java)
    }

    // --- International alert sources ---

    @Provides
    @Singleton
    @Named("meteoalarm")
    fun provideMeteoAlarmRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(MeteoAlarmApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideMeteoAlarmApi(@Named("meteoalarm") retrofit: Retrofit): MeteoAlarmApi {
        return retrofit.create(MeteoAlarmApi::class.java)
    }

    @Provides
    @Singleton
    @Named("jma")
    fun provideJmaRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(JmaAlertApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideJmaAlertApi(@Named("jma") retrofit: Retrofit): JmaAlertApi {
        return retrofit.create(JmaAlertApi::class.java)
    }

    @Provides
    @Singleton
    @Named("eccc")
    fun provideEnvironmentCanadaRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(EnvironmentCanadaAlertApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideEnvironmentCanadaAlertApi(@Named("eccc") retrofit: Retrofit): EnvironmentCanadaAlertApi {
        return retrofit.create(EnvironmentCanadaAlertApi::class.java)
    }

    /**
     * ECCC forecast API lives at a different host
     * (api.weather.gc.ca vs. weather.gc.ca for alerts), so a separate
     * Retrofit instance is required. Both are keyless, both honour the
     * global User-Agent + retry interceptors.
     */
    @Provides
    @Singleton
    @Named("eccc_forecast")
    fun provideEnvironmentCanadaForecastRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(EnvironmentCanadaForecastApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideEnvironmentCanadaForecastApi(
        @Named("eccc_forecast") retrofit: Retrofit,
    ): EnvironmentCanadaForecastApi {
        return retrofit.create(EnvironmentCanadaForecastApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAlertAdapters(
        nwsAdapter: NwsAlertAdapter,
        meteoAlarmAdapter: MeteoAlarmAdapter,
        jmaAdapter: JmaAlertAdapter,
        ecccAdapter: EnvironmentCanadaAlertAdapter,
    ): Set<@JvmSuppressWildcards AlertSourceAdapter> {
        return setOf(nwsAdapter, meteoAlarmAdapter, jmaAdapter, ecccAdapter)
    }

    // --- OpenWeatherMap ---

    @Provides
    @Singleton
    @Named("owm")
    fun provideOwmRetrofit(client: OkHttpClient): Retrofit {
        // OWM free tier: 60 calls/min, 1M/month. Cap at ~1 req/s with burst 5.
        // Certificate pinning mitigates MITM exfiltration of the user's
        // `?appid=<key>` API key on untrusted networks (see ApiCertificatePins).
        val owmClient = client.newBuilder()
            .addInterceptor(RateLimitInterceptor("openweathermap", rate = 1.0, burst = 5))
            .certificatePinner(ApiCertificatePins.build())
            .build()
        return Retrofit.Builder()
            .baseUrl(OpenWeatherMapApi.BASE_URL)
            .client(owmClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenWeatherMapApi(@Named("owm") retrofit: Retrofit): OpenWeatherMapApi {
        return retrofit.create(OpenWeatherMapApi::class.java)
    }

    /** Separate OWM instance for Air Pollution (different base URL). */
    @Provides
    @Singleton
    @Named("owm_aqi")
    fun provideOwmAqiRetrofit(client: OkHttpClient): Retrofit {
        // Same pinner as OWM forecast — same vendor TLS terminator.
        val owmAqiClient = client.newBuilder()
            .addInterceptor(RateLimitInterceptor("openweathermap-aqi", rate = 1.0, burst = 5))
            .certificatePinner(ApiCertificatePins.build())
            .build()
        return Retrofit.Builder()
            .baseUrl(OpenWeatherMapApi.AIR_POLLUTION_BASE_URL)
            .client(owmAqiClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("owm_aqi")
    fun provideOwmAqiApi(@Named("owm_aqi") retrofit: Retrofit): OpenWeatherMapApi {
        return retrofit.create(OpenWeatherMapApi::class.java)
    }

    // --- Pirate Weather ---

    @Provides
    @Singleton
    @Named("pirateweather")
    fun providePirateWeatherRetrofit(client: OkHttpClient): Retrofit {
        // Pirate Weather free tier: ~10,000 calls/month ≈ 14/hour — keep a
        // conservative per-second cap so widget refresh + main screen burst
        // doesn't eat the hourly budget on rapid location switches.
        // Pirate Weather embeds the API key in the URL path
        // (/forecast/{key}/lat,lon) so a MITM can steal it silently —
        // pinning is as critical here as for OWM.
        val pwClient = client.newBuilder()
            .addInterceptor(RateLimitInterceptor("pirateweather", rate = 0.5, burst = 4))
            .certificatePinner(ApiCertificatePins.build())
            .build()
        return Retrofit.Builder()
            .baseUrl(PirateWeatherApi.BASE_URL)
            .client(pwClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePirateWeatherApi(@Named("pirateweather") retrofit: Retrofit): PirateWeatherApi {
        return retrofit.create(PirateWeatherApi::class.java)
    }

    // --- MET Norway (LocationForecast 2.0) ---

    @Provides
    @Singleton
    @Named("metnorway")
    fun provideMetNorwayRetrofit(client: OkHttpClient): Retrofit {
        // MET's terms ban the default `okhttp/X.Y` User-Agent. The global
        // interceptor already sets `ZeusWatch/<ver> (Android; Open-Source)`
        // which is compliant. Rate limiting is their 20 req/s aggregate
        // cap — we sit far below that on the phone, so no extra throttle
        // here beyond the global retry interceptor.
        return Retrofit.Builder()
            .baseUrl(MetNorwayApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideMetNorwayApi(@Named("metnorway") retrofit: Retrofit): MetNorwayApi {
        return retrofit.create(MetNorwayApi::class.java)
    }

    // --- Bright Sky (DWD) ---

    @Provides
    @Singleton
    @Named("brightsky")
    fun provideBrightSkyRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BrightSkyApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideBrightSkyApi(@Named("brightsky") retrofit: Retrofit): BrightSkyApi {
        return retrofit.create(BrightSkyApi::class.java)
    }
}
