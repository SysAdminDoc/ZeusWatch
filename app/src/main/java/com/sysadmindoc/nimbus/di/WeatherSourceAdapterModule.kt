package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.data.api.BmkgAlertAdapter
import com.sysadmindoc.nimbus.data.api.GeoSphereAustriaAlertAdapter
import com.sysadmindoc.nimbus.data.api.GeoSphereAustriaNowcastAdapter
import com.sysadmindoc.nimbus.data.repository.AlertSourceManagerAdapter
import com.sysadmindoc.nimbus.data.repository.AlertSourceRequest
import com.sysadmindoc.nimbus.data.repository.AlertSourcePreference
import com.sysadmindoc.nimbus.data.repository.BrightSkyAlertAdapter
import com.sysadmindoc.nimbus.data.repository.BrightSkyForecastAdapter
import com.sysadmindoc.nimbus.data.repository.CoordinateSourceRequest
import com.sysadmindoc.nimbus.data.repository.EnvironmentCanadaForecastAdapter
import com.sysadmindoc.nimbus.data.repository.FmiForecastAdapter
import com.sysadmindoc.nimbus.data.repository.ForecastSourceRequest
import com.sysadmindoc.nimbus.data.repository.HkoAlertAdapter
import com.sysadmindoc.nimbus.data.repository.HkoForecastAdapter
import com.sysadmindoc.nimbus.data.repository.MetNorwayForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoAqiAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoBomForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoDmiForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoKmaForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoMeteoFranceForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoMeteoFranceMinutelyAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoMinutelyAdapter
import com.sysadmindoc.nimbus.data.repository.OpenMeteoUkmoForecastAdapter
import com.sysadmindoc.nimbus.data.repository.OwmAlertAdapter
import com.sysadmindoc.nimbus.data.repository.OwmAqiAdapter
import com.sysadmindoc.nimbus.data.repository.OwmForecastAdapter
import com.sysadmindoc.nimbus.data.repository.PirateWeatherForecastAdapter
import com.sysadmindoc.nimbus.data.repository.WeatherDataType
import com.sysadmindoc.nimbus.data.repository.WeatherSourceAdapter
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@MapKey
annotation class WeatherSourceKey(val value: WeatherSourceProvider)

@Module
@InstallIn(SingletonComponent::class)
object WeatherSourceAdapterModule {

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_METEO)
    fun provideOpenMeteoAdapter(
        forecastAdapter: OpenMeteoForecastAdapter,
        aqiAdapter: OpenMeteoAqiAdapter,
        minutelyAdapter: OpenMeteoMinutelyAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.OPEN_METEO
        override val supportedTypes = setOf(
            WeatherDataType.FORECAST,
            WeatherDataType.AIR_QUALITY,
            WeatherDataType.MINUTELY,
        )

        override suspend fun getForecast(request: ForecastSourceRequest) =
            forecastAdapter.getWeather(request.latitude, request.longitude, request.locationName)

        override suspend fun getAirQuality(request: CoordinateSourceRequest) =
            aqiAdapter.getAirQuality(request.latitude, request.longitude)

        override suspend fun getMinutely(request: CoordinateSourceRequest) =
            minutelyAdapter.getMinutelyPrecipitation(request.latitude, request.longitude)
    }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_METEO_BOM)
    fun provideOpenMeteoBomAdapter(adapter: OpenMeteoBomForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(WeatherSourceProvider.OPEN_METEO_BOM) { request ->
            adapter.getWeather(request.latitude, request.longitude, request.locationName)
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_METEO_KMA)
    fun provideOpenMeteoKmaAdapter(adapter: OpenMeteoKmaForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(WeatherSourceProvider.OPEN_METEO_KMA) { request ->
            adapter.getWeather(request.latitude, request.longitude, request.locationName)
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_METEO_UKMO)
    fun provideOpenMeteoUkmoAdapter(adapter: OpenMeteoUkmoForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(WeatherSourceProvider.OPEN_METEO_UKMO) { request ->
            adapter.getWeather(request.latitude, request.longitude, request.locationName)
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_METEO_DMI)
    fun provideOpenMeteoDmiAdapter(adapter: OpenMeteoDmiForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(WeatherSourceProvider.OPEN_METEO_DMI) { request ->
            adapter.getWeather(request.latitude, request.longitude, request.locationName)
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_METEO_METEO_FRANCE)
    fun provideOpenMeteoMeteoFranceAdapter(
        forecastAdapter: OpenMeteoMeteoFranceForecastAdapter,
        minutelyAdapter: OpenMeteoMeteoFranceMinutelyAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.OPEN_METEO_METEO_FRANCE
        override val supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.MINUTELY)

        override suspend fun getForecast(request: ForecastSourceRequest) =
            forecastAdapter.getWeather(request.latitude, request.longitude, request.locationName)

        override suspend fun getMinutely(request: CoordinateSourceRequest) =
            minutelyAdapter.getMinutelyPrecipitation(request.latitude, request.longitude)
    }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.FMI)
    fun provideFmiAdapter(adapter: FmiForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(
            provider = WeatherSourceProvider.FMI,
            requiresForecastZone = true,
        ) { request ->
            adapter.getWeather(
                request.latitude,
                request.longitude,
                request.locationName,
                requireNotNull(request.forecastZone),
            )
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.NWS)
    fun provideNwsAlertAdapter(adapter: AlertSourceManagerAdapter): WeatherSourceAdapter =
        alertManagerAdapter(WeatherSourceProvider.NWS, adapter)

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.METEOALARM)
    fun provideMeteoAlarmAlertAdapter(adapter: AlertSourceManagerAdapter): WeatherSourceAdapter =
        alertManagerAdapter(WeatherSourceProvider.METEOALARM, adapter)

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.JMA)
    fun provideJmaAlertAdapter(adapter: AlertSourceManagerAdapter): WeatherSourceAdapter =
        alertManagerAdapter(WeatherSourceProvider.JMA, adapter)

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.OPEN_WEATHER_MAP)
    fun provideOwmAdapter(
        forecastAdapter: OwmForecastAdapter,
        alertAdapter: OwmAlertAdapter,
        aqiAdapter: OwmAqiAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.OPEN_WEATHER_MAP
        override val supportedTypes = setOf(
            WeatherDataType.FORECAST,
            WeatherDataType.ALERTS,
            WeatherDataType.AIR_QUALITY,
        )

        override suspend fun getForecast(request: ForecastSourceRequest) =
            forecastAdapter.getWeather(request.latitude, request.longitude, request.locationName)

        override suspend fun getAlerts(request: AlertSourceRequest) =
            alertAdapter.getAlerts(request.latitude, request.longitude)

        override suspend fun getAirQuality(request: CoordinateSourceRequest) =
            aqiAdapter.getAirQuality(request.latitude, request.longitude)
    }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.PIRATE_WEATHER)
    fun providePirateWeatherAdapter(adapter: PirateWeatherForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(WeatherSourceProvider.PIRATE_WEATHER) { request ->
            adapter.getWeather(request.latitude, request.longitude, request.locationName)
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.BRIGHT_SKY)
    fun provideBrightSkyAdapter(
        forecastAdapter: BrightSkyForecastAdapter,
        alertAdapter: BrightSkyAlertAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.BRIGHT_SKY
        override val supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS)
        override val requiresForecastZone = true

        override suspend fun getForecast(request: ForecastSourceRequest) =
            forecastAdapter.getWeather(
                request.latitude,
                request.longitude,
                request.locationName,
                requireNotNull(request.forecastZone),
            )

        override suspend fun getAlerts(request: AlertSourceRequest) =
            alertAdapter.getAlerts(request.latitude, request.longitude)
    }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.MET_NORWAY)
    fun provideMetNorwayAdapter(adapter: MetNorwayForecastAdapter): WeatherSourceAdapter =
        forecastOnlyAdapter(
            provider = WeatherSourceProvider.MET_NORWAY,
            requiresForecastZone = true,
        ) { request ->
            adapter.getWeather(
                request.latitude,
                request.longitude,
                request.locationName,
                requireNotNull(request.forecastZone),
            )
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.ENVIRONMENT_CANADA)
    fun provideEnvironmentCanadaAdapter(
        forecastAdapter: EnvironmentCanadaForecastAdapter,
        alertAdapter: AlertSourceManagerAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.ENVIRONMENT_CANADA
        override val supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS)
        override val requiresForecastZone = true

        override suspend fun getForecast(request: ForecastSourceRequest) =
            forecastAdapter.getWeather(
                request.latitude,
                request.longitude,
                request.locationName,
                requireNotNull(request.forecastZone),
            )

        override suspend fun getAlerts(request: AlertSourceRequest) =
            alertAdapter.getAlerts(
                latitude = request.latitude,
                longitude = request.longitude,
                preferenceOverride = AlertSourcePreference.ECCC_ONLY,
                includeMeteredSources = request.includeMeteredSources,
                countryHint = request.countryHint,
            )

        override suspend fun getAlertsDetailed(request: AlertSourceRequest) =
            alertAdapter.getAlertsDetailed(
                latitude = request.latitude,
                longitude = request.longitude,
                preferenceOverride = AlertSourcePreference.ECCC_ONLY,
                includeMeteredSources = request.includeMeteredSources,
                countryHint = request.countryHint,
            )
    }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.HKO)
    fun provideHkoAdapter(
        forecastAdapter: HkoForecastAdapter,
        alertAdapter: HkoAlertAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.HKO
        override val supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS)

        override suspend fun getForecast(request: ForecastSourceRequest) =
            forecastAdapter.getWeather(request.latitude, request.longitude, request.locationName)

        override suspend fun getAlerts(request: AlertSourceRequest) =
            alertAdapter.getAlerts(request.latitude, request.longitude)

        override suspend fun getAlertsDetailed(request: AlertSourceRequest) =
            alertAdapter.getAlertsDetailed(request.latitude, request.longitude)
    }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.BMKG)
    fun provideBmkgAdapter(alertAdapter: BmkgAlertAdapter): WeatherSourceAdapter =
        alertOnlyAdapter(WeatherSourceProvider.BMKG) { request ->
            alertAdapter.getAlerts(request.latitude, request.longitude)
        }

    @Provides
    @Singleton
    @IntoMap
    @WeatherSourceKey(WeatherSourceProvider.GEOSPHERE_AUSTRIA)
    fun provideGeoSphereAustriaAdapter(
        alertAdapter: GeoSphereAustriaAlertAdapter,
        nowcastAdapter: GeoSphereAustriaNowcastAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = WeatherSourceProvider.GEOSPHERE_AUSTRIA
        override val supportedTypes = setOf(WeatherDataType.ALERTS, WeatherDataType.MINUTELY)

        override suspend fun getAlerts(request: AlertSourceRequest) =
            alertAdapter.getAlerts(request.latitude, request.longitude)

        override suspend fun getMinutely(request: CoordinateSourceRequest) =
            nowcastAdapter.getMinutelyPrecipitation(request.latitude, request.longitude)
    }

    private fun forecastOnlyAdapter(
        provider: WeatherSourceProvider,
        requiresForecastZone: Boolean = false,
        fetch: suspend (ForecastSourceRequest) -> Result<WeatherData>,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = provider
        override val supportedTypes = setOf(WeatherDataType.FORECAST)
        override val requiresForecastZone = requiresForecastZone
        override suspend fun getForecast(request: ForecastSourceRequest) = fetch(request)
    }

    private fun alertOnlyAdapter(
        provider: WeatherSourceProvider,
        fetch: suspend (AlertSourceRequest) -> Result<List<WeatherAlert>>,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = provider
        override val supportedTypes = setOf(WeatherDataType.ALERTS)
        override suspend fun getAlerts(request: AlertSourceRequest) = fetch(request)
    }

    private fun alertManagerAdapter(
        provider: WeatherSourceProvider,
        adapter: AlertSourceManagerAdapter,
    ): WeatherSourceAdapter = object : WeatherSourceAdapter {
        override val provider = provider
        override val supportedTypes = setOf(WeatherDataType.ALERTS)

        override suspend fun getAlerts(request: AlertSourceRequest) =
            adapter.getAlerts(
                latitude = request.latitude,
                longitude = request.longitude,
                preferenceOverride = provider.toAlertSourcePreferenceOverride(),
                includeMeteredSources = request.includeMeteredSources,
                countryHint = request.countryHint,
            )

        override suspend fun getAlertsDetailed(request: AlertSourceRequest) =
            adapter.getAlertsDetailed(
                latitude = request.latitude,
                longitude = request.longitude,
                preferenceOverride = provider.toAlertSourcePreferenceOverride(),
                includeMeteredSources = request.includeMeteredSources,
                countryHint = request.countryHint,
            )
    }
}

private fun WeatherSourceProvider.toAlertSourcePreferenceOverride(): AlertSourcePreference? =
    takeUnless { it == WeatherSourceProvider.defaultFor(WeatherDataType.ALERTS) }
        ?.let { provider ->
            when (provider) {
                WeatherSourceProvider.NWS -> AlertSourcePreference.NWS_ONLY
                WeatherSourceProvider.METEOALARM -> AlertSourcePreference.METEOALARM_ONLY
                WeatherSourceProvider.JMA -> AlertSourcePreference.JMA_ONLY
                WeatherSourceProvider.ENVIRONMENT_CANADA -> AlertSourcePreference.ECCC_ONLY
                else -> error("${provider.displayName} does not map to an alert source override")
            }
        }
