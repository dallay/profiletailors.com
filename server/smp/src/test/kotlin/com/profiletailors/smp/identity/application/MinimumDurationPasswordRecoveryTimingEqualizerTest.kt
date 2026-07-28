package com.profiletailors.smp.identity.application

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class MinimumDurationPasswordRecoveryTimingEqualizerTest {

    @Test
    fun `waits only for the minimum duration remaining after account-dependent work`() = runTest {
        val timeSource = SequenceMonotonicTimeSource(1_000_000_000L, 1_075_000_000L)
        val delays = mutableListOf<Duration>()
        val equalizer = MinimumDurationPasswordRecoveryTimingEqualizer(
            minimumDuration = Duration.ofMillis(250),
            timeSource = timeSource,
            suspendingDelay = SuspendingDelay(delays::add),
        )

        val startedAt = equalizer.markStart()
        equalizer.equalize(startedAt)

        assertEquals(listOf(Duration.ofMillis(175)), delays)
    }

    @Test
    fun `does not wait when account-dependent work already exceeds the minimum duration`() = runTest {
        val delays = mutableListOf<Duration>()
        val equalizer = MinimumDurationPasswordRecoveryTimingEqualizer(
            minimumDuration = Duration.ofMillis(250),
            timeSource = SequenceMonotonicTimeSource(1_000_000_000L, 1_300_000_000L),
            suspendingDelay = SuspendingDelay(delays::add),
        )

        equalizer.equalize(equalizer.markStart())

        assertTrue(delays.isEmpty())
    }

    @Test
    fun `propagates cancellation from the suspending delay`() = runTest {
        val equalizer = MinimumDurationPasswordRecoveryTimingEqualizer(
            minimumDuration = Duration.ofMillis(250),
            timeSource = SequenceMonotonicTimeSource(1_000_000_000L, 1_000_000_000L),
            suspendingDelay = SuspendingDelay { throw CancellationException("cancelled") },
        )

        val error = assertThrows<CancellationException> {
            equalizer.equalize(equalizer.markStart())
        }

        assertEquals("cancelled", error.message)
    }

    private class SequenceMonotonicTimeSource(vararg readings: Long) : MonotonicTimeSource {
        private val values = ArrayDeque(readings.toList())

        override fun readNanos(): Long = values.removeFirst()
    }
}
