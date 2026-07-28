package com.profiletailors.smp.identity.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordResetTokenExceptionsTest {

    @Test
    fun `InvalidPasswordResetTokenException carries the public reset detail`() {
        val exception = InvalidPasswordResetTokenException()
        assertEquals(
            "This password reset link is invalid or has expired. Request a new one.",
            exception.message,
        )
    }

    @Test
    fun `ExpiredPasswordResetTokenException is a subtype of InvalidPasswordResetTokenException`() {
        val exception = ExpiredPasswordResetTokenException()
        assertTrue(exception is InvalidPasswordResetTokenException)
        assertEquals(
            "This password reset link is invalid or has expired. Request a new one.",
            exception.message,
        )
    }

    @Test
    fun `UsedPasswordResetTokenException is a subtype of InvalidPasswordResetTokenException`() {
        val exception = UsedPasswordResetTokenException()
        assertTrue(exception is InvalidPasswordResetTokenException)
        assertEquals(
            "This password reset link is invalid or has expired. Request a new one.",
            exception.message,
        )
    }

    @Test
    fun `PasswordRecoveryDisabledException carries its public message`() {
        val exception = PasswordRecoveryDisabledException()
        assertEquals("Password recovery is disabled.", exception.message)
    }

    @Test
    fun `InvalidPasswordException receives the password for logging only and surfaces the message`() {
        val exception = PasswordRecoveryPasswordException(password = "secret")
        assertEquals("Password does not meet policy requirements.", exception.message)
    }
}
