package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.CloseAccountCommand
import com.profiletailors.smp.identity.application.CloseAccountHandler
import com.profiletailors.smp.identity.application.CloseAccountRateLimitException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AccountLifecycleControllerTest {

    private val closeAccountHandler: CloseAccountHandler = mockk()
    private val controller = AccountLifecycleController(
        closeAccountHandler = closeAccountHandler,
        principalContextProvider = FakePrincipalContextProvider(),
    )

    @Nested
    inner class CloseAccount {

        @Test
        fun `returns 200 when closure succeeds`() = runTest {
            coEvery { closeAccountHandler.handle(any()) } returns Unit

            val response = controller.closeAccount(CloseAccountRequestDto(confirmation = "DELETE"))

            assertEquals(HttpStatus.OK, response.statusCode)
            coVerify(exactly = 1) { closeAccountHandler.handle(any<CloseAccountCommand>()) }
        }

        @Test
        fun `throws 400 when handler throws IllegalArgumentException`() = runTest {
            coEvery { closeAccountHandler.handle(any()) } throws
                IllegalArgumentException("Confirmation text must equal DELETE")

            val result = kotlin.runCatching {
                controller.closeAccount(CloseAccountRequestDto(confirmation = "WRONG"))
            }

            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is ResponseStatusException)
            assertEquals(HttpStatus.BAD_REQUEST, (exception as ResponseStatusException).statusCode)
        }

        @Test
        fun `propagates rate limit exception`() = runTest {
            coEvery { closeAccountHandler.handle(any()) } throws CloseAccountRateLimitException("Rate limit exceeded")

            val result = kotlin.runCatching {
                controller.closeAccount(CloseAccountRequestDto(confirmation = "DELETE"))
            }

            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is CloseAccountRateLimitException)
            assertEquals("Rate limit exceeded", exception!!.message)
        }
    }
}
