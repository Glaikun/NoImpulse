package com.glaikun.noimpulse.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FrictionSessionLedgerTest {

    @Test
    fun `isInSession defaults to false`() {
        val ledger = FrictionSessionLedger()
        assertFalse(ledger.isInSession("com.twitter.android"))
    }

    @Test
    fun `markPassed makes a package in-session`() {
        val ledger = FrictionSessionLedger()
        ledger.markPassed("com.twitter.android")
        assertTrue(ledger.isInSession("com.twitter.android"))
    }

    @Test
    fun `markPassed is idempotent`() {
        val ledger = FrictionSessionLedger()
        ledger.markPassed("com.twitter.android")
        ledger.markPassed("com.twitter.android")
        assertTrue(ledger.isInSession("com.twitter.android"))
    }

    @Test
    fun `markPassed scoped per package`() {
        val ledger = FrictionSessionLedger()
        ledger.markPassed("com.twitter.android")
        assertFalse(ledger.isInSession("com.tiktok.android"))
    }

    @Test
    fun `clearAll resets every entry`() {
        val ledger = FrictionSessionLedger()
        ledger.markPassed("com.twitter.android")
        ledger.markPassed("com.tiktok.android")
        ledger.clearAll()
        assertFalse(ledger.isInSession("com.twitter.android"))
        assertFalse(ledger.isInSession("com.tiktok.android"))
    }

    @Test
    fun `concurrent markPassed and isInSession does not throw`() {
        val ledger = FrictionSessionLedger()
        val pool = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(1)
        val done = CountDownLatch(16)

        repeat(8) { i ->
            pool.submit {
                ready.await()
                repeat(1_000) { ledger.markPassed("com.app.$i") }
                done.countDown()
            }
            pool.submit {
                ready.await()
                repeat(1_000) { ledger.isInSession("com.app.$i") }
                done.countDown()
            }
        }

        ready.countDown()
        assertTrue("Workers timed out", done.await(5, TimeUnit.SECONDS))
        pool.shutdown()

        // Every "writer" thread should have left its package in the set.
        repeat(8) { i -> assertTrue(ledger.isInSession("com.app.$i")) }
    }
}
