package com.sysadmindoc.nimbus.di

import android.content.Context
import com.sysadmindoc.nimbus.data.api.NimbusDatabase
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.api.WeatherDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NimbusDatabase =
        NimbusDatabase.buildOn(context)

    @Provides
    @Singleton
    fun provideWeatherDao(db: NimbusDatabase): WeatherDao = db.weatherDao()

    @Provides
    @Singleton
    fun provideSavedLocationDao(db: NimbusDatabase): SavedLocationDao = db.savedLocationDao()
}
