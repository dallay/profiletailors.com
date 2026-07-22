package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.CloseAccountCommand
import com.profiletailors.smp.identity.application.CloseAccountConfirmationException
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

class AccountLifecycleControllerTest {

    private val closeAccountHandler: CloseAccountHandler = mockk()
    private val controller = AccountLifecycleController(
        closeAccountHandler = closeAccountHandler,
        principalContextProvider = FakePrincipalContextProvider(),
    )

    @Nested
    inner class CloseAccount {

        @Test
        fun `returns 204 when closure succeeds`() = runTest {
            coEvery { closeAccountHandler.handle(any()) } returns Unit

            val response = controller.closeAccount(CloseAccountRequestDto(confirmation = "DELETE"))

            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
            coVerify(exactly = 1) { closeAccountHandler.handle(any<CloseAccountCommand>()) }
        }

        @Test
        fun `propagates confirmation exception to problem details handler`() = runTest {
            coEvery { closeAccountHandler.handle(any()) } throws
                CloseAccountConfirmationException("Confirmation text must equal DELETE")

            val result = kotlin.runCatching {
                controller.closeAccount(CloseAccountRequestDto(confirmation = "WRONG"))
            }

            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is CloseAccountConfirmationException)
            assertEquals("Confirmation text must equal DELETE", exception!!.message)
        }

        @Test
        fun `propagates rate limit exception to problem details handler`() = runTest {
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
