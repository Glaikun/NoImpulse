package com.glaikun.noimpulse.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory record of packages that have already passed friction during the
 * current screen-on session. The accessibility watcher consults it to decide
 * whether to re-trigger friction when an app returns to the foreground.
 *
 * Cleared on [Intent.ACTION_SCREEN_OFF][android.content.Intent.ACTION_SCREEN_OFF]
 * (the watcher registers a receiver) and on process death — both are intentional.
 * No persistence; sessions are meant to be ephemeral.
 *
 * Backed by [ConcurrentHashMap] so the accessibility service thread and the main
 * thread can both touch it without external synchronisation.
 */
@Singleton
class FrictionSessionLedger @Inject constructor() {
    private val passed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun markPassed(packageName: String) {
        passed.add(packageName)
    }

    fun isInSession(packageName: String): Boolean = packageName in passed

    fun clearAll() {
        passed.clear()
    }
}
