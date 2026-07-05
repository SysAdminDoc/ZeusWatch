package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sysadmindoc.nimbus.data.api.AirQualityApi
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.ApiCertificatePins
import com.sysadmindoc.nimbus.data.api.BmkgAlertAdapter
import com.sysadmindoc.nimbus.data.api.BmkgAlertApi
import com.sysadmindoc.nimbus.data.api.BrightSkyApi
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaAlertAdapter
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaAlertApi
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaForecastApi
import com.sysadmindoc.nimbus.data.api.FmiForecastApi
import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.GeoSphereAustriaDatasetApi
import com.sysadmindoc.nimbus.data.api.GeoSphereAustriaWarnApi
import com.sysadmindoc.nimbus.data.api.HkoApi
import com.sysadmindoc.nimbus.data.api.JmaAlertAdapter
import com.sysadmindoc.nimbus.data.api.JmaAlertApi
import com.sysadmindoc.nimbus.data.api.MetNorwayApi
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmApi
import com.sysadmindoc.nimbus.data.api.NoaaSwpcApi
import com.sysadmindoc.nimbus.data.api.NwsAlertAdapter
import com.sysadmindoc.nimbus.data.api.OpenMeteoClimateApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoEnsembleApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoFloodApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoMarineApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoPreviousRunsApi
import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoArchiveApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoSingleRunApi
import com.sysadmindoc.nimbus.data.api.OpenWeatherMapApi
import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.api.PirateWeatherAlertAdapter
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import com.sysadmindoc.nimbus.data.api.RateLimitInterceptor
import com.sysadmindoc.nimbus.data.api.WmoAlertAdapter
import com.sysadmindoc.nimbus.data.api.WmoAlertApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pirate Weather embeds its API key as a path segment
 * (`/forecast/{key}/{lat},{lon}` and `/forecast/{key},{exclude}/{lat},{lon}`),
 * so OkHttp query-param redaction cannot see it. Anchor on the `pirateweather.net/forecast/`
 * host+prefix and redact the whole next segment. Host-anchoring (rather than a
 * lookahead on the following coordinate) means the key is redacted regardless of
 * how the coordinate is formatted (`+`-prefixed, percent-encoded, …), while
 * never touching unrelated `/forecast/…` paths on other hosts (e.g. NWS
 * `/gridpoints/…/forecast/hourly`).
 */
private val PIRATE_WEATHER_PATH_KEY_REGEX = Regex(
    "(pirateweather\\.net/forecast/)[^/?#\\s]+",
    RegexOption.IGNORE_CASE,
)

internal fun redactPirateWeatherPathKey(message: String): String =
    PIRATE_WEATHER_PATH_KEY_REGEX.replace(message, "$1***")

private object ApiKeyRedactingLogger : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        android.util.Log.d("OkHttp", redactPirateWeatherPathKey(message))
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
     * Shared Retrofit assembly. Every weather/alert/third-party API uses the
     * same kotlinx-serialization JSON converter over the app's OkHttp client,
     * so each provider collapses to a single call. Providers needing bespoke
     * headers, interceptors, or certificate pinning pass a customized [client]
     * (built via `client.newBuilder()`) and otherwise share this one line.
     */
    private fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor(ApiKeyRedactingLogger).apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactQueryParams("appid", "apikey", "api_key", "key")
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
    fun provideWeatherRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoApi.BASE_URL, client)

    @Provides
    @Singleton
    @Named("geocoding")
    fun provideGeocodingRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(GeocodingApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoApi(@Named("weather") retrofit: Retrofit): OpenMeteoApi {
        return retrofit.create(OpenMeteoApi::class.java)
    }

    @Provides
    @Singleton
    @Named("archive")
    fun provideArchiveRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoArchiveApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoArchiveApi(@Named("archive") retrofit: Retrofit): OpenMeteoArchiveApi {
        return retrofit.create(OpenMeteoArchiveApi::class.java)
    }

    @Provides
    @Singleton
    @Named("single_runs")
    fun provideSingleRunsRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoSingleRunApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoSingleRunApi(@Named("single_runs") retrofit: Retrofit): OpenMeteoSingleRunApi {
        return retrofit.create(OpenMeteoSingleRunApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeocodingApi(@Named("geocoding") retrofit: Retrofit): GeocodingApi {
        return retrofit.create(GeocodingApi::class.java)
    }

    @Provides
    @Singleton
    @Named("rainviewer")
    fun provideRainViewerRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(RainViewerApi.BASE_URL, client)

    @Provides
    @Singleton
    @Named("rainviewer")
    fun provideRainViewerApi(@Named("rainviewer") retrofit: Retrofit): RainViewerApi {
        return retrofit.create(RainViewerApi::class.java)
    }

    @Provides
    @Singleton
    @Named("librewxr")
    fun provideLibreWxrRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(RainViewerApi.LIBREWXR_BASE_URL, client)

    @Provides
    @Singleton
    @Named("librewxr")
    fun provideLibreWxrApi(@Named("librewxr") retrofit: Retrofit): RainViewerApi {
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
        return buildRetrofit(NwsAlertApi.BASE_URL, nwsClient)
    }

    @Provides
    @Singleton
    fun provideNwsAlertApi(@Named("nws") retrofit: Retrofit): NwsAlertApi {
        return retrofit.create(NwsAlertApi::class.java)
    }

    @Provides
    @Singleton
    @Named("airquality")
    fun provideAirQualityRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(AirQualityApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideAirQualityApi(@Named("airquality") retrofit: Retrofit): AirQualityApi {
        return retrofit.create(AirQualityApi::class.java)
    }

    // --- International alert sources ---

    @Provides
    @Singleton
    @Named("meteoalarm")
    fun provideMeteoAlarmRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(MeteoAlarmApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideMeteoAlarmApi(@Named("meteoalarm") retrofit: Retrofit): MeteoAlarmApi {
        return retrofit.create(MeteoAlarmApi::class.java)
    }

    @Provides
    @Singleton
    @Named("jma")
    fun provideJmaRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(JmaAlertApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideJmaAlertApi(@Named("jma") retrofit: Retrofit): JmaAlertApi {
        return retrofit.create(JmaAlertApi::class.java)
    }

    @Provides
    @Singleton
    @Named("eccc")
    fun provideEnvironmentCanadaRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(EnvironmentCanadaAlertApi.BASE_URL, client)

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
    fun provideEnvironmentCanadaForecastRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(EnvironmentCanadaForecastApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideEnvironmentCanadaForecastApi(
        @Named("eccc_forecast") retrofit: Retrofit,
    ): EnvironmentCanadaForecastApi {
        return retrofit.create(EnvironmentCanadaForecastApi::class.java)
    }

    @Provides
    @Singleton
    @Named("fmi")
    fun provideFmiForecastRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(FmiForecastApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideFmiForecastApi(@Named("fmi") retrofit: Retrofit): FmiForecastApi {
        return retrofit.create(FmiForecastApi::class.java)
    }

    @Provides
    @Singleton
    @Named("hko")
    fun provideHkoRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(HkoApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideHkoApi(@Named("hko") retrofit: Retrofit): HkoApi {
        return retrofit.create(HkoApi::class.java)
    }

    private val bmkgRateLimiter = RateLimitInterceptor("bmkg", rate = 1.0, burst = 10)

    @Provides
    @Singleton
    @Named("bmkg")
    fun provideBmkgRetrofit(client: OkHttpClient): Retrofit {
        val bmkgClient = client.newBuilder()
            .addInterceptor(bmkgRateLimiter)
            .build()
        return buildRetrofit(BmkgAlertApi.BASE_URL, bmkgClient)
    }

    @Provides
    @Singleton
    fun provideBmkgAlertApi(@Named("bmkg") retrofit: Retrofit): BmkgAlertApi {
        return retrofit.create(BmkgAlertApi::class.java)
    }

    @Provides
    @Singleton
    @Named("geosphere_dataset")
    fun provideGeoSphereAustriaDatasetRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(GeoSphereAustriaDatasetApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideGeoSphereAustriaDatasetApi(
        @Named("geosphere_dataset") retrofit: Retrofit,
    ): GeoSphereAustriaDatasetApi {
        return retrofit.create(GeoSphereAustriaDatasetApi::class.java)
    }

    @Provides
    @Singleton
    @Named("geosphere_warn")
    fun provideGeoSphereAustriaWarnRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(GeoSphereAustriaWarnApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideGeoSphereAustriaWarnApi(
        @Named("geosphere_warn") retrofit: Retrofit,
    ): GeoSphereAustriaWarnApi {
        return retrofit.create(GeoSphereAustriaWarnApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAlertAdapters(
        nwsAdapter: NwsAlertAdapter,
        meteoAlarmAdapter: MeteoAlarmAdapter,
        jmaAdapter: JmaAlertAdapter,
        ecccAdapter: EnvironmentCanadaAlertAdapter,
        bmkgAdapter: BmkgAlertAdapter,
        wmoAdapter: WmoAlertAdapter,
        pirateWeatherAlertAdapter: PirateWeatherAlertAdapter,
    ): Set<@JvmSuppressWildcards AlertSourceAdapter> {
        return setOf(
            nwsAdapter,
            meteoAlarmAdapter,
            jmaAdapter,
            ecccAdapter,
            bmkgAdapter,
            wmoAdapter,
            pirateWeatherAlertAdapter,
        )
    }

    // --- WMO Severe Weather ---

    @Provides
    @Singleton
    @Named("wmo")
    fun provideWmoRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(WmoAlertApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideWmoAlertApi(@Named("wmo") retrofit: Retrofit): WmoAlertApi {
        return retrofit.create(WmoAlertApi::class.java)
    }

    // --- OpenWeatherMap ---

    private val owmRateLimiter = RateLimitInterceptor("openweathermap", rate = 1.0, burst = 5)

    @Provides
    @Singleton
    @Named("owm")
    fun provideOwmRetrofit(client: OkHttpClient): Retrofit {
        val owmClient = client.newBuilder()
            .addInterceptor(owmRateLimiter)
            .certificatePinner(ApiCertificatePins.build())
            .build()
        return buildRetrofit(OpenWeatherMapApi.BASE_URL, owmClient)
    }

    @Provides
    @Singleton
    fun provideOpenWeatherMapApi(@Named("owm") retrofit: Retrofit): OpenWeatherMapApi {
        return retrofit.create(OpenWeatherMapApi::class.java)
    }

    @Provides
    @Singleton
    @Named("owm_aqi")
    fun provideOwmAqiRetrofit(client: OkHttpClient): Retrofit {
        val owmAqiClient = client.newBuilder()
            .addInterceptor(owmRateLimiter)
            .certificatePinner(ApiCertificatePins.build())
            .build()
        return buildRetrofit(OpenWeatherMapApi.AIR_POLLUTION_BASE_URL, owmAqiClient)
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
        return buildRetrofit(PirateWeatherApi.BASE_URL, pwClient)
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
        return buildRetrofit(MetNorwayApi.BASE_URL, client)
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
    fun provideBrightSkyRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(BrightSkyApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideBrightSkyApi(@Named("brightsky") retrofit: Retrofit): BrightSkyApi {
        return retrofit.create(BrightSkyApi::class.java)
    }

    // --- NOAA SWPC (Aurora / Kp Index) ---

    @Provides
    @Singleton
    @Named("noaa_swpc")
    fun provideNoaaSwpcRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(NoaaSwpcApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideNoaaSwpcApi(@Named("noaa_swpc") retrofit: Retrofit): NoaaSwpcApi {
        return retrofit.create(NoaaSwpcApi::class.java)
    }

    // --- Open-Meteo Marine ---

    @Provides
    @Singleton
    @Named("marine")
    fun provideMarineRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoMarineApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoMarineApi(@Named("marine") retrofit: Retrofit): OpenMeteoMarineApi {
        return retrofit.create(OpenMeteoMarineApi::class.java)
    }

    // --- Open-Meteo Flood ---

    @Provides
    @Singleton
    @Named("flood")
    fun provideFloodRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoFloodApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoFloodApi(@Named("flood") retrofit: Retrofit): OpenMeteoFloodApi {
        return retrofit.create(OpenMeteoFloodApi::class.java)
    }

    // --- Open-Meteo Previous Runs (forecast accuracy) ---

    @Provides
    @Singleton
    @Named("previous_runs")
    fun providePreviousRunsRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoPreviousRunsApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoPreviousRunsApi(@Named("previous_runs") retrofit: Retrofit): OpenMeteoPreviousRunsApi {
        return retrofit.create(OpenMeteoPreviousRunsApi::class.java)
    }

    // --- Open-Meteo Ensemble (confidence bands) ---

    @Provides
    @Singleton
    @Named("ensemble")
    fun provideEnsembleRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoEnsembleApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoEnsembleApi(@Named("ensemble") retrofit: Retrofit): OpenMeteoEnsembleApi {
        return retrofit.create(OpenMeteoEnsembleApi::class.java)
    }

    // --- Open-Meteo Climate (CMIP6 projections) ---

    @Provides
    @Singleton
    @Named("climate")
    fun provideClimateRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(OpenMeteoClimateApi.BASE_URL, client)

    @Provides
    @Singleton
    fun provideOpenMeteoClimateApi(@Named("climate") retrofit: Retrofit): OpenMeteoClimateApi {
        return retrofit.create(OpenMeteoClimateApi::class.java)
    }
}
