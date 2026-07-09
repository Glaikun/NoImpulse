package com.glaikun.noimpulse

import com.glaikun.noimpulse.data.AccessibilityStatusSource
import com.glaikun.noimpulse.data.LauncherAppsSource
import com.glaikun.noimpulse.data.SettingsRepository
import com.glaikun.noimpulse.data.UsageStatsSource
import com.glaikun.noimpulse.di.AppModule
import com.glaikun.noimpulse.di.IoDispatcher
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.testing.FakeAccessibilityStatusSource
import com.glaikun.noimpulse.testing.FakeLauncherAppsSource
import com.glaikun.noimpulse.testing.FakeSettingsRepository
import com.glaikun.noimpulse.testing.FakeUsageStatsSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Replaces [AppModule] for the real-device e2e suite (`@HiltAndroidTest` tests under
 * `app/src/androidTest`). Keeps the win these tests are for — a real `MainActivity`
 * launch with its actual Hilt dependency graph resolved — while swapping the
 * system-facing sources for the same kind of hand-written fakes the JVM integration
 * suite uses, so the scenario is deterministic regardless of what's really installed
 * on the test device.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    /** The one non-essential, non-allowlisted app the e2e scenario gates behind friction. */
    val testApp = AppEntry("Test Twitter", "com.glaikun.noimpulse.e2etest.twitter")

    @Provides
    @Singleton
    fun provideUsageStatsSource(): UsageStatsSource = FakeUsageStatsSource()

    @Provides
    @Singleton
    fun provideLauncherAppsSource(): LauncherAppsSource =
        FakeLauncherAppsSource(installed = listOf(testApp))

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository =
        FakeSettingsRepository(introSeen = false, setupComplete = false)

    @Provides
    @Singleton
    fun provideAccessibilityStatusSource(): AccessibilityStatusSource = FakeAccessibilityStatusSource()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
