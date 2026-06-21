package com.glaikun.noimpulse.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glaikun.noimpulse.api.DailyUsage
import com.glaikun.noimpulse.di.IoDispatcher
import com.glaikun.noimpulse.interfaces.UsageStatsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class HomeViewModel @Inject constructor(
    app: Application,
    private val usageStats: UsageStatsSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(app) {

    data class UiState(
        val time: String = "",
        val date: String = "",
        val batteryPercent: Int = -1,
        val pickupCount: Int? = null,        // null = permission not granted
        val screenOnMinutes: Int? = null,
        val allowedApps: List<String> = ALLOWED_APPS,
    )

    /**
     * Each source streams at its own natural cadence and they are combined into the
     * UI state. Collection (and therefore every loop/receiver below) is alive only
     * while the screen is observing, thanks to [SharingStarted.WhileSubscribed].
     */
    val state: StateFlow<UiState> =
        combine(minuteTicks(), batteryPercent(), dailyUsage()) { _, battery, usage ->
            UiState(
                time = formatTime(),
                date = formatDate(),
                batteryPercent = battery,
                pickupCount = usage?.pickupCount,
                screenOnMinutes = usage?.screenOnMinutes,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState(),
        )

    /** Emits immediately, then once on every wall-clock minute boundary (drift-free). */
    private fun minuteTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val now = LocalTime.now()
            val msToNextMinute = (60L - now.second) * 1_000L - now.nano / 1_000_000L
            delay(msToNextMinute.milliseconds)
        }
    }

    /** Emits the current battery percentage, then on every battery-change broadcast. */
    private fun batteryPercent(): Flow<Int> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(readBattery(intent))
            }
        }
        val sticky = getApplication<Application>()
            .registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        // Seed combine with a value straight away so it can produce its first state.
        trySend(sticky?.let { readBattery(it) } ?: -1)
        awaitClose { getApplication<Application>().unregisterReceiver(receiver) }
    }

    /** Polls usage stats off the main thread at a human cadence. */
    private fun dailyUsage(): Flow<DailyUsage?> = flow {
        while (true) {
            emit(usageStats.queryToday())
            delay(USAGE_POLL_INTERVAL_MS.milliseconds)
        }
    }.flowOn(ioDispatcher)

    private fun readBattery(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level == -1 || scale == 0) -1 else (level * 100) / scale
    }

    companion object {
        val ALLOWED_APPS = listOf("Phone", "Messages", "Maps", "Clock", "Calculator")

        private const val STOP_TIMEOUT_MS = 5_000L
        private const val USAGE_POLL_INTERVAL_MS = 30_000L

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

        internal fun formatTime(): String = LocalTime.now().format(TIME_FORMAT)
        internal fun formatDate(): String = LocalDate.now().format(DATE_FORMAT)
    }
}

/** Formats a minute count into a human-readable string, e.g. "2h 15m". */
internal fun formatHours(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"
