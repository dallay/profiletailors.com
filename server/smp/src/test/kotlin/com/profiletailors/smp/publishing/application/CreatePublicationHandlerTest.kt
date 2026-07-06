package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CreatePublicationHandlerTest {
    private val principalContextProvider = mockk<PrincipalContextProvider>(relaxed = true)
    private val resourceContextProvider = mockk<ResourceContextProvider>(relaxed = true)
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

    private val handler = CreatePublicationHandler(
        principalContextProvider,
        resourceContextProvider,
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
    fun `should create new publication`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        val accountId = "acc-1"

        val principalCtx = PrincipalContext(principalId, PrincipalType.USER, principalId)
        coEvery { principalContextProvider.require() } returns principalCtx

        val resourceCtx = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
        every { resourceContextProvider.require() } returns resourceCtx

        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, accountId) } returns
            SocialAccount(
                id = accountId,
                socialConnectionId = "conn-1",
                workspaceId = workspaceId,
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "p-acc-1",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Account",
                status = SocialConnectionStatus.ACTIVE,
            )
        coEvery {
            publicationRepository.createDraft(any<PublicationDraft>())
        } answers { it.invocation.args[0] as PublicationDraft }

        val command = CreatePublicationCommand(
            socialAccountId = accountId,
            title = "Title",
            bodyText = "Body",
            scheduleMode = ScheduleMode.NOW,
        )

        val result = handler.handle(command)

        result.title shouldBe "Title"
        result.workspaceId shouldBe workspaceId
        result.status shouldBe PublicationStatus.QUEUED
    }
}
