package com.glaikun.noimpulse.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.glaikun.noimpulse.data.DataStoreSettingsRepository
import com.glaikun.noimpulse.data.SystemAccessibilityStatusSource
import com.glaikun.noimpulse.data.SystemLauncherAppsSource
import com.glaikun.noimpulse.data.AccessibilityStatusSource
import com.glaikun.noimpulse.data.LauncherAppsSource
import com.glaikun.noimpulse.data.SettingsRepository
import com.glaikun.noimpulse.data.UsageStatsSource
import com.glaikun.noimpulse.data.SystemUsageStatsSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindUsageStatsSource(impl: SystemUsageStatsSource): UsageStatsSource

    @Binds
    abstract fun bindLauncherAppsSource(impl: SystemLauncherAppsSource): LauncherAppsSource

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindAccessibilityStatusSource(
        impl: SystemAccessibilityStatusSource,
    ): AccessibilityStatusSource

    companion object {
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.dataStore
    }
}
