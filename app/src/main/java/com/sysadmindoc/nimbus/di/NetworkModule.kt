package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sysadmindoc.nimbus.data.api.AirQualityApi
import com.sysadmindoc.nimbus.data.api.AlertSourceAdapter
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaAlertAdapter
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaAlertApi
import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.JmaAlertAdapter
import com.sysadmindoc.nimbus.data.api.JmaAlertApi
import com.sysadmindoc.nimbus.data.api.MeteoAlarmAdapter
import com.sysadmindoc.nimbus.data.api.MeteoAlarmApi
import com.sysadmindoc.nimbus.data.api.NwsAlertAdapter
import com.sysadmindoc.nimbus.data.api.NwsAlertApi
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.RainViewerApi
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Retry interceptor: retries on IOException up to 2 times with exponential backoff. */
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var lastException: IOException? = null
        for (attempt in 0..2) {
            try {
                return@Interceptor chain.proceed(request)
            } catch (e: IOException) {
                lastException = e
                if (attempt < 2) {
                    try {
                        Thread.sleep(1000L * (1 shl attempt)) // 1s, 2s
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Retry interrupted", interrupted)
                    }
                }
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
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
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
}
