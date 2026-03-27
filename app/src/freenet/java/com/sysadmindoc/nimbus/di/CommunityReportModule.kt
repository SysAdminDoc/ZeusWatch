package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.data.repository.CommunityReportSource
import com.sysadmindoc.nimbus.data.repository.FreenetCommunityReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityReportModule {
    @Binds
    @Singleton
    abstract fun bindCommunityReportSource(impl: FreenetCommunityReportRepository): CommunityReportSource
}
