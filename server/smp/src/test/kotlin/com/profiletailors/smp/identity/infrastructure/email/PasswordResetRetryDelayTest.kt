package com.profiletailors.smp.identity.infrastructure.email

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertTrue
import kotlin.time.measureTime

class PasswordResetRetryDelayTest {
    @Test
    fun `zero delay returns immediately`() = runTest {
        val elapsed = measureTime {
            coroutinePasswordResetRetryDelay.await(Duration.ZERO)
        }
        assertTrue(elapsed.inWholeMilliseconds < 1_000)
    }
}
