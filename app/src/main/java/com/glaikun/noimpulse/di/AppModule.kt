package com.glaikun.noimpulse.di

import com.glaikun.noimpulse.interfaces.UsageStatsSource
import com.glaikun.noimpulse.ui.SystemUsageStatsSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindUsageStatsSource(impl: SystemUsageStatsSource): UsageStatsSource

    companion object {
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
