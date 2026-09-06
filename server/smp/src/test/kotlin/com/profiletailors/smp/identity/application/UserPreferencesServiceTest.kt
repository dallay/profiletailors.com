package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserPreferencesServiceTest {

    private val userPreferencesGateway = mockk<UserPreferencesGateway>()
    private val service = UserPreferencesService(userPreferencesGateway)

    @Test
    fun `getPreferences returns stored preferences when present`() = runTest {
        val stored = UserPreferences(
            principalId = "user-1",
            locale = "es",
            timezone = "Europe/Madrid",
            timeFormat = "24h",
            dateFormat = "DD/MM/YYYY",
            weekStartsOn = "Monday",
            theme = "dark",
        )
        coEvery { userPreferencesGateway.findByPrincipalId("user-1") } returns stored

        val result = service.getPreferences("user-1")

        assertEquals("es", result.locale)
        assertEquals("Europe/Madrid", result.timezone)
    }

    @Test
    fun `getPreferences returns default preferences when not found`() = runTest {
        coEvery { userPreferencesGateway.findByPrincipalId("user-2") } returns null

        val result = service.getPreferences("user-2")

        assertEquals("en", result.locale)
        assertEquals("UTC", result.timezone)
        assertEquals("dark", result.theme)
    }

    @Test
    fun `updatePreferences saves updated values`() = runTest {
        coEvery { userPreferencesGateway.findByPrincipalId("user-1") } returns null
        coEvery { userPreferencesGateway.save(any()) } answers { firstArg() }

        val updated = service.updatePreferences(
            "user-1",
            UpdateUserPreferencesCommand(
                locale = "es",
                timezone = "America/New_York",
                timeFormat = "12h",
                dateFormat = "MM/DD/YYYY",
                weekStartsOn = "Sunday",
                theme = "light",
            ),
        )

        assertEquals("es", updated.locale)
        assertEquals("America/New_York", updated.timezone)
        assertEquals("light", updated.theme)
        coVerify { userPreferencesGateway.save(match { it.locale == "es" && it.theme == "light" }) }
    }
}
