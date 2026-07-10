package com.glaikun.noimpulse

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in [HiltTestApplication] so `@HiltAndroidTest`-annotated instrumentation tests
 * can launch a real `@AndroidEntryPoint` activity (`MainActivity`) with its dependency
 * graph resolved by Hilt, same as production — just with [TestAppModule]'s fakes
 * standing in for the system-facing sources.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
