package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.OAuthAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.OAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAuthorizationRegistry
import com.profiletailors.smp.publishing.domain.ProviderConnectionRegistry
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ProviderCompletionReplayTest {
    private val now = Instant.parse("2026-09-26T12:00:00Z")
    private val payload = OAuthStatePayload(
        SocialProvider.THREADS, "workspace", "principal", "https://app.example/callback", "nonce",
        now, now.plusSeconds(600),
    )
    private val signer = mockk<OAuthStateSigner>(relaxed = true)
    private val builder = mockk<OAuthAuthorizationUrlBuilder>()
    private val provider = mockk<SocialConnectionProvider>()
    private val handler = CompleteProviderConnectionHandler(
        principalContextProvider = object : PrincipalContextProvider {
            override suspend fun current() = PrincipalContext("principal", PrincipalType.USER, "user@example.com")
        },
        resourceContextProvider = object : ResourceContextProvider {
            override fun current() = ResourceContext(ResourceContextType.WORKSPACE, workspaceId = "workspace")
        },
        connectionRegistry = ProviderConnectionRegistry.from(SocialProvider.THREADS to provider),
        oauthStateSigner = signer,
        authorizationRegistry = ProviderAuthorizationRegistry.from(SocialProvider.THREADS to builder),
        socialConnectionRepository = mockk(),
        socialAccountRepository = mockk(),
        channelEventPublisher = mockk(),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        transactionRunner = mockk(),
    )
    private val command = CompleteProviderConnectionCommand(
        SocialProvider.THREADS, "code", payload.redirectUri, "state",
    )

    @Test
    fun `should preserve nonce when callback binding or redirect validation fails`() = runTest {
        every { builder.isAllowedRedirectUri(any()) } returns true
        listOf(
            payload.copy(workspaceId = "another-workspace"),
            payload.copy(principalId = "another-principal"),
            payload.copy(redirectUri = "https://other.example/callback"),
            payload.copy(provider = SocialProvider.LINKEDIN),
        ).forEach { invalid ->
            every { signer.verify("state") } returns invalid
            shouldThrow<InvalidOAuthStateException> { handler.handle(command) }
        }
        every { signer.verify("state") } returns payload
        every { builder.isAllowedRedirectUri(any()) } returns false
        shouldThrow<InvalidOAuthStateException> { handler.handle(command) }

        coVerify(exactly = 0) { signer.consume(any()) }
        coVerify(exactly = 0) { provider.completeConnection(any()) }
    }

    @Test
    fun `should prevent provider exchange when nonce has already been consumed`() = runTest {
        every { signer.verify("state") } returns payload
        every { builder.isAllowedRedirectUri(any()) } returns true
        coEvery { signer.consume(payload) } throws InvalidOAuthStateException("Already consumed")

        shouldThrow<InvalidOAuthStateException> { handler.handle(command) }

        coVerify(exactly = 1) { signer.consume(payload) }
        coVerify(exactly = 0) { provider.completeConnection(any()) }
    }
}
