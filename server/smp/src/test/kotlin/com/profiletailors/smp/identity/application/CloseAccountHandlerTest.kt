package com.profiletailors.smp.identity.application

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CloseAccountHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC)

    private val orchestrationPort: CloseAccountOrchestrationPort = mockk(relaxUnitFun = true)
    private val rateLimitPort: RateLimitPort = mockk {
        every { tryAcquire(any(), any(), any()) } returns true
    }

    private val handler = CloseAccountHandler(
        orchestrationPort = orchestrationPort,
        rateLimitPort = rateLimitPort,
        clock = fixedClock,
    )

    @Nested
    inner class Validation {

        @Test
        fun `throws when confirmation is not DELETE`() = runTest {
            val result = kotlin.runCatching {
                handler.handle(
                    CloseAccountCommand(
                        principalId = "principal-1",
                        confirmation = "WRONG",
                    ),
                )
            }
            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is CloseAccountConfirmationException)
            assert(exception!!.message!!.contains("DELETE"))
        }

        @Test
        fun `accepts valid confirmation and invokes orchestration port`() = runTest {
            every { rateLimitPort.tryAcquire(any(), any(), any()) } returns true

            handler.handle(
                CloseAccountCommand(
                    principalId = "principal-1",
                    confirmation = "DELETE",
                ),
            )

            coVerify { orchestrationPort.execute("principal-1") }
        }

        @Test
        fun `does not call orchestration when confirmation is wrong`() = runTest {
            val result = kotlin.runCatching {
                handler.handle(
                    CloseAccountCommand(
                        principalId = "principal-1",
                        confirmation = "INVALID",
                    ),
                )
            }
            assert(result.isFailure)
            coVerify(exactly = 0) { orchestrationPort.execute(any()) }
        }
    }

    @Nested
    inner class RateLimiting {

        @Test
        fun `throws rate limit exception when rate limit port rejects`() = runTest {
            every { rateLimitPort.tryAcquire(any(), any(), any()) } returns false

            val result = kotlin.runCatching {
                handler.handle(CloseAccountCommand(principalId = "rate-limited-user", confirmation = "DELETE"))
            }
            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is CloseAccountRateLimitException)
            assert(exception!!.message!!.contains("rate limit", ignoreCase = true))
        }

        @Test
        fun `does not call orchestration when rate limited`() = runTest {
            every { rateLimitPort.tryAcquire(any(), any(), any()) } returns false

            kotlin.runCatching {
                handler.handle(CloseAccountCommand(principalId = "rate-limited-user", confirmation = "DELETE"))
            }
            coVerify(exactly = 0) { orchestrationPort.execute(any()) }
        }
    }
}
