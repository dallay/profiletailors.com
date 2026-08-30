@file:Suppress("MaxLineLength")

package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PublicationCreationServiceTest {
    private val socialAccountRepository = mockk<SocialAccountRepository>(relaxed = true)
    private val publicationRepository = mockk<PublicationRepository>(relaxed = true)
    private val publicationAssetRepository = mockk<PublicationAssetRepository>(relaxed = true)
    private val publicationJobRepository = mockk<PublicationJobRepository>(relaxed = true)
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
    private val providerCapabilityValidator = mockk<ProviderCapabilityValidator>(relaxed = true)
    private val schedulingPolicy = PublicationSchedulingPolicy()
    private val mediaAssetResolver = mockk<MediaAssetResolver>(relaxed = true)
    private val mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = false)
    private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    private val service = PublicationCreationService(
        socialAccountRepository,
        publicationRepository,
        publicationAssetRepository,
        publicationJobRepository,
        transactionRunner,
        providerCapabilityValidator,
        schedulingPolicy,
        mediaAssetResolver,
        mediaIntegrationSettings,
        clock,
    )

    @Test
    fun `should create publication with external media url`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        val accountId = "acc-1"
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, accountId) } returns SocialAccount(
            id = accountId,
            socialConnectionId = "conn-1",
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "p-acc-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Account",
            status = SocialConnectionStatus.ACTIVE,
        )
        coEvery { publicationRepository.createDraft(any()) } answers
            { it.invocation.args[0] as com.profiletailors.smp.publishing.domain.PublicationDraft }
        val result = service.create(
            workspaceId = workspaceId,
            principalId = principalId,
            socialAccountId = accountId,
            bodyText = "Hello bulk",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            mediaUrls = listOf("https://cdn.example.com/image.jpg"),
        )
        result.bodyText shouldBe "Hello bulk"
        result.status shouldBe PublicationStatus.SCHEDULED
    }

    @Test
    fun `should reject private url`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        val accountId = "acc-1"
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, accountId) } returns SocialAccount(
            id = accountId,
            socialConnectionId = "conn-1",
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "p-acc-1",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Account",
            status = SocialConnectionStatus.ACTIVE,
        )
        assertThrows<PublicationValidationException> {
            service.create(
                workspaceId = workspaceId,
                principalId = principalId,
                socialAccountId = accountId,
                bodyText = "Hello",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                mediaUrls = listOf("http://127.0.0.1/evil.jpg"),
            )
        }
    }

    @Test
    fun `should handle bulk placeholder via active lookup`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-bulk-placeholder") } returns null
        coEvery { socialAccountRepository.findFirstActiveByWorkspace(workspaceId) } returns SocialAccount(
            id = "acc-active-1",
            socialConnectionId = "conn-1",
            workspaceId = workspaceId,
            provider = SocialProvider.LINKEDIN,
            providerAccountId = "p-active",
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Active Account",
            status = SocialConnectionStatus.ACTIVE,
        )
        coEvery { publicationRepository.createDraft(any()) } answers
            { it.invocation.args[0] as com.profiletailors.smp.publishing.domain.PublicationDraft }
        val result = service.create(
            workspaceId = workspaceId,
            principalId = principalId,
            socialAccountId = "acc-bulk-placeholder",
            bodyText = "Bulk placeholder",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
        )
        result.socialAccountId shouldBe "acc-active-1"
    }

    @Test
    fun `should reject when no active account`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-bulk-placeholder") } returns null
        coEvery { socialAccountRepository.findFirstActiveByWorkspace(workspaceId) } returns null
        assertThrows<PublicationValidationException> {
            service.create(
                workspaceId = workspaceId,
                principalId = principalId,
                socialAccountId = "acc-bulk-placeholder",
                bodyText = "Bulk placeholder",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            )
        }
    }
}
