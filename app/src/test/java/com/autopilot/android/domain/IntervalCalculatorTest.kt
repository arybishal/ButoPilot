package com.autopilot.android.domain

import com.autopilot.android.domain.validation.IntervalCalculator
import org.junit.Assert.*
import org.junit.Test

class IntervalCalculatorTest {

    @Test
    fun getNextRefreshDelayMs_fixedIntervals() {
        val validIntervals = listOf(10, 15, 20, 30, 45, 60, 90, 120)
        for (sec in validIntervals) {
            val delay = IntervalCalculator.getNextRefreshDelayMs(
                fixedSec = sec,
                randomize = false,
                minSec = 10,
                maxSec = 30
            )
            assertEquals(sec * 1000L, delay)
        }
    }

    @Test
    fun getNextScrollDelayMs_fixedIntervals() {
        val validScrollIntervals = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
        for (sec in validScrollIntervals) {
            val delay = IntervalCalculator.getNextScrollDelayMs(
                fixedSec = sec,
                randomize = false,
                minSec = 5,
                maxSec = 25
            )
            assertEquals(sec * 1000L, delay)
        }
    }

    @Test
    fun getNextRefreshDelayMs_randomizedStaysWithinBounds() {
        val minSec = 15
        val maxSec = 45

        for (i in 1..100) {
            val delayMs = IntervalCalculator.getNextRefreshDelayMs(
                fixedSec = 30,
                randomize = true,
                minSec = minSec,
                maxSec = maxSec
            )
            val delaySec = delayMs / 1000
            assertTrue("Delay $delaySec must be >= $minSec", delaySec >= minSec)
            assertTrue("Delay $delaySec must be <= $maxSec", delaySec <= maxSec)
        }
    }

    @Test
    fun getNextScrollDelayMs_randomizedStaysWithinBounds() {
        val minSec = 5
        val maxSec = 20

        for (i in 1..100) {
            val delayMs = IntervalCalculator.getNextScrollDelayMs(
                fixedSec = 10,
                randomize = true,
                minSec = minSec,
                maxSec = maxSec
            )
            val delaySec = delayMs / 1000
            assertTrue("Delay $delaySec must be >= $minSec", delaySec >= minSec)
            assertTrue("Delay $delaySec must be <= $maxSec", delaySec <= maxSec)
        }
    }

    @Test
    fun getNextRefreshDelayMs_invertedBoundsHandledGracefully() {
        val delayMs = IntervalCalculator.getNextRefreshDelayMs(
            fixedSec = 30,
            randomize = true,
            minSec = 50,
            maxSec = 20
        )
        val delaySec = delayMs / 1000
        assertTrue(delaySec in 20..50)
    }
}
