package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AccountSecurityServiceTest {

    private val localPasswordCredentialGateway = mockk<LocalPasswordCredentialGateway>()
    private val passwordHasher = mockk<PasswordHasher>()
    private val principalIdentityLookup = mockk<PrincipalIdentityLookup>()
    private val refreshSessionLifecycleService = mockk<RefreshSessionLifecycleService>(relaxed = true)

    private val service = AccountSecurityService(
        localPasswordCredentialGateway = localPasswordCredentialGateway,
        passwordHasher = passwordHasher,
        principalIdentityLookup = principalIdentityLookup,
        refreshSessionLifecycleService = refreshSessionLifecycleService,
    )

    @Test
    fun `getSecurityCapabilities returns local password capability when credential exists`() = runTest {
        coEvery { localPasswordCredentialGateway.findByPrincipalId("user-1") } returns LocalPasswordCredentialRecord(
            principalId = "user-1",
            email = "user@example.com",
            username = "user",
            passwordHash = "hash123",
        )
        coEvery { principalIdentityLookup.findByPrincipalId("user-1") } returns PrincipalIdentityFacts(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "local:user@example.com",
            provider = null,
            displayIdentity = "user",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )

        val caps = service.getSecurityCapabilities("user-1")

        assertTrue(caps.hasLocalPassword)
        assertEquals(1, caps.signInMethods.size)
        assertEquals("password", caps.signInMethods[0].provider)
        assertEquals("user@example.com", caps.signInMethods[0].identifier)
    }

    @Test
    fun `getSecurityCapabilities returns OAuth method when provider is set`() = runTest {
        coEvery { localPasswordCredentialGateway.findByPrincipalId("user-oauth") } returns null
        coEvery { principalIdentityLookup.findByPrincipalId("user-oauth") } returns PrincipalIdentityFacts(
            principalId = "user-oauth",
            principalType = PrincipalType.USER,
            subject = "google:123456",
            provider = "google",
            displayIdentity = "oauth user",
            email = "oauth@example.com",
            username = null,
            emailStatus = EmailStatus.VERIFIED,
        )

        val caps = service.getSecurityCapabilities("user-oauth")

        assertFalse(caps.hasLocalPassword)
        assertEquals(1, caps.signInMethods.size)
        assertEquals("google", caps.signInMethods[0].provider)
        assertEquals("CONNECTED", caps.signInMethods[0].status)
    }

    @Test
    fun `changePassword successfully updates password hash and revokes other sessions`() = runTest {
        val credential = LocalPasswordCredentialRecord(
            principalId = "user-1",
            email = "user@example.com",
            username = "user",
            passwordHash = "oldHash",
        )
        coEvery { localPasswordCredentialGateway.findByPrincipalId("user-1") } returns credential
        coEvery { passwordHasher.matches("oldPassword123", "oldHash") } returns true
        coEvery { passwordHasher.hash("newPassword1234") } returns "newHash"
        coEvery { localPasswordCredentialGateway.updatePasswordHash("user-1", "newHash") } returns Unit

        service.changePassword(
            ChangePasswordCommand(
                principalId = "user-1",
                currentPassword = "oldPassword123",
                newPassword = "newPassword1234",
                rawRefreshToken = "current-refresh-token",
            ),
        )

        coVerify { localPasswordCredentialGateway.updatePasswordHash("user-1", "newHash") }
        coVerify { refreshSessionLifecycleService.revokeOthersForPrincipal("user-1", "current-refresh-token") }
    }

    @Test
    fun `changePassword rejects incorrect current password`() = runTest {
        val credential = LocalPasswordCredentialRecord(
            principalId = "user-1",
            email = "user@example.com",
            username = "user",
            passwordHash = "oldHash",
        )
        coEvery { localPasswordCredentialGateway.findByPrincipalId("user-1") } returns credential
        coEvery { passwordHasher.matches("wrongPassword", "oldHash") } returns false

        assertThrows<InvalidCurrentPasswordException> {
            service.changePassword(
                ChangePasswordCommand(
                    principalId = "user-1",
                    currentPassword = "wrongPassword",
                    newPassword = "newPassword1234",
                ),
            )
        }
    }

    @Test
    fun `changePassword throws if local password credential does not exist`() = runTest {
        coEvery { localPasswordCredentialGateway.findByPrincipalId("user-oauth") } returns null

        assertThrows<LocalPasswordCredentialNotFoundException> {
            service.changePassword(
                ChangePasswordCommand(
                    principalId = "user-oauth",
                    currentPassword = "anyPassword123",
                    newPassword = "newPassword1234",
                ),
            )
        }
    }
}
