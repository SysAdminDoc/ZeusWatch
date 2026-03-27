package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.data.repository.CommunityReportRepository
import com.sysadmindoc.nimbus.data.repository.CommunityReportSource
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
    abstract fun bindCommunityReportSource(impl: CommunityReportRepository): CommunityReportSource
}
