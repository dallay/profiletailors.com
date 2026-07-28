package com.profiletailors.smp.identity.application

import kotlinx.coroutines.delay
import java.time.Duration

interface PasswordRecoveryTimingEqualizer {
    fun markStart(): Long

    suspend fun equalize(startedAtNanos: Long)
}

fun interface MonotonicTimeSource {
    fun readNanos(): Long
}

fun interface SuspendingDelay {
    suspend fun wait(duration: Duration)
}

class MinimumDurationPasswordRecoveryTimingEqualizer(
    private val minimumDuration: Duration,
    private val timeSource: MonotonicTimeSource = MonotonicTimeSource(System::nanoTime),
    private val suspendingDelay: SuspendingDelay = SuspendingDelay { delay(it.toMillis()) },
) : PasswordRecoveryTimingEqualizer {
    init {
        require(!minimumDuration.isNegative) { "Minimum response duration must not be negative." }
    }

    override fun markStart(): Long = timeSource.readNanos()

    override suspend fun equalize(startedAtNanos: Long) {
        val elapsedNanos = (timeSource.readNanos() - startedAtNanos).coerceAtLeast(0L)
        val remaining = minimumDuration.minusNanos(elapsedNanos)
        if (!remaining.isNegative && !remaining.isZero) {
            suspendingDelay.wait(remaining)
        }
    }
}
