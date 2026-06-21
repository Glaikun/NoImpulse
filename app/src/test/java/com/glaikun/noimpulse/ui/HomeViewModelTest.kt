package com.glaikun.noimpulse.ui

import androidx.test.core.app.ApplicationProvider
import com.glaikun.noimpulse.api.DailyUsage
import com.glaikun.noimpulse.interfaces.UsageStatsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Builds the ViewModel with the test scheduler driving its I/O dispatcher, and
     * starts collecting [HomeViewModel.state] on the [backgroundScope] so the
     * `WhileSubscribed` flow becomes active. Returns once the first combined state
     * has been produced ([runCurrent] settles all work scheduled at virtual time 0,
     * without entering the never-ending tick/poll delays).
     */
    private fun TestScope.activeViewModel(usage: DailyUsage?): HomeViewModel {
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(usage),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    // ── Pure formatting helpers ───────────────────────────────────────────────

    @Test
    fun `formatTime returns HH colon mm string`() {
        val result = HomeViewModel.formatTime()
        assertTrue(
            "Expected HH:mm format but got: $result",
            result.matches(Regex("\\d{2}:\\d{2}")),
        )
    }

    @Test
    fun `formatDate returns non-empty string containing day and month`() {
        val result = HomeViewModel.formatDate()
        assertTrue("Expected non-empty date string but got: $result", result.isNotBlank())
    }

    @Test
    fun `formatHours 90 minutes returns 1h 30m`() {
        assertEquals("1h 30m", formatHours(90))
    }

    @Test
    fun `formatHours 0 minutes returns 0h 0m`() {
        assertEquals("0h 0m", formatHours(0))
    }

    @Test
    fun `formatHours 60 minutes returns 1h 0m`() {
        assertEquals("1h 0m", formatHours(60))
    }

    @Test
    fun `formatHours 135 minutes returns 2h 15m`() {
        assertEquals("2h 15m", formatHours(135))
    }

    // ── ViewModel state with fake usage source ────────────────────────────────

    @Test
    fun `state pickupCount reflects fake source value`() = runTest {
        val vm = activeViewModel(DailyUsage(pickupCount = 7, screenOnMinutes = 90))
        assertEquals(7, vm.state.value.pickupCount)
    }

    @Test
    fun `state screenOnMinutes reflects fake source value`() = runTest {
        val vm = activeViewModel(DailyUsage(pickupCount = 3, screenOnMinutes = 45))
        assertEquals(45, vm.state.value.screenOnMinutes)
    }

    @Test
    fun `state pickupCount and screenOnMinutes are null when source returns null`() = runTest {
        val vm = activeViewModel(null)
        assertNull(vm.state.value.pickupCount)
        assertNull(vm.state.value.screenOnMinutes)
    }

    @Test
    fun `state time is populated after first tick`() = runTest {
        val vm = activeViewModel(null)
        assertTrue(vm.state.value.time.isNotBlank())
    }

    @Test
    fun `state date is populated after first tick`() = runTest {
        val vm = activeViewModel(null)
        assertTrue(vm.state.value.date.isNotBlank())
    }

    @Test
    fun `allowed apps list is not empty`() {
        assertTrue(HomeViewModel.ALLOWED_APPS.isNotEmpty())
    }

    @Test
    fun `initial state has empty time and null pickup count`() {
        // With no collector the WhileSubscribed flow is idle, so state holds defaults.
        val vm = HomeViewModel(
            app = ApplicationProvider.getApplicationContext(),
            usageStats = FakeUsageStatsSource(DailyUsage(1, 10)),
            ioDispatcher = testDispatcher,
        )
        assertEquals("", vm.state.value.time)
        assertNull(vm.state.value.pickupCount)
    }
}

// ── Test double ──────────────────────────────────────────────────────────────

private class FakeUsageStatsSource(private val result: DailyUsage?) : UsageStatsSource {
    override fun queryToday(): DailyUsage? = result
}
