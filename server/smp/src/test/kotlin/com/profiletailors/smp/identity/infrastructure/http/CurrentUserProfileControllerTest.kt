package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.CurrentUserProfile
import com.profiletailors.smp.identity.application.GetCurrentUserProfileHandler
import com.profiletailors.smp.identity.domain.EmailStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrentUserProfileControllerTest {

    @Test
    fun `returns current user profile`() = runTest {
        val expected = CurrentUserProfile(
            principalId = "user-1",
            email = "yuniel@example.com",
            username = "yuniel",
            displayIdentity = "yuniel",
            emailStatus = EmailStatus.PENDING,
        )
        val controller = CurrentUserProfileController(FakeGetCurrentUserProfileHandler(expected))

        val response = controller.currentUser()

        assertEquals(expected, response.body)
        assertEquals(EmailStatus.PENDING, response.body?.emailStatus)
    }

    private class FakeGetCurrentUserProfileHandler(private val result: CurrentUserProfile) :
        GetCurrentUserProfileHandler(
            principalContextProvider = FakePrincipalContextProvider(),
            principalIdentityLookup = FakePrincipalIdentityLookup(),
        ) {
        override suspend fun handle(): CurrentUserProfile = result
    }
}
