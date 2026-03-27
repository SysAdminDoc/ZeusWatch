package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.util.FreenetSummaryEngine
import com.sysadmindoc.nimbus.util.SummaryEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SummaryModule {
    @Binds
    @Singleton
    abstract fun bindSummaryEngine(impl: FreenetSummaryEngine): SummaryEngine
}
