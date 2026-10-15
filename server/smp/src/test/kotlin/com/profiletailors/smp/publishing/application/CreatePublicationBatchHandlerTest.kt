package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CreatePublicationBatchHandlerTest {
    private val principalContextProvider = mockk<PrincipalContextProvider>(relaxed = true)
    private val resourceContextProvider = mockk<ResourceContextProvider>(relaxed = true)
    private val socialAccountRepository = mockk<SocialAccountRepository>(relaxed = true)
    private val publicationRepository = mockk<PublicationRepository>(relaxed = true)
    private val publicationAssetRepository = mockk<PublicationAssetRepository>(relaxed = true)
    private val publicationJobRepository = mockk<PublicationJobRepository>(relaxed = true)
    private var transactionCount = 0
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            transactionCount += 1
            return block()
        }
    }
    private val providerCapabilityValidator = mockk<ProviderCapabilityValidator>(relaxed = true)
    private val schedulingPolicy = PublicationSchedulingPolicy()
    private val mediaAssetResolver = mockk<MediaAssetResolver>(relaxed = true)
    private val mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = false)
    private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    private val handler = CreatePublicationBatchHandler(
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

    private fun linkedInAccount(workspaceId: String) = SocialAccount(
        id = "acc-li",
        socialConnectionId = "conn-li",
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "p-li",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "WireMock Dev User",
        status = SocialConnectionStatus.ACTIVE,
    )

    private fun threadsAccount(workspaceId: String) = SocialAccount(
        id = "acc-th",
        socialConnectionId = "conn-th",
        workspaceId = workspaceId,
        provider = SocialProvider.THREADS,
        providerAccountId = "p-th",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "WireMock Threads User",
        status = SocialConnectionStatus.ACTIVE,
    )

    @Test
    fun `creates every target when all validators pass`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        val createdDrafts = mutableListOf<PublicationDraft>()
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-li") } returns
            linkedInAccount(workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-th") } returns
            threadsAccount(workspaceId)
        coEvery { publicationRepository.createDraft(any<PublicationDraft>()) } coAnswers {
            val draft = it.invocation.args[0] as PublicationDraft
            createdDrafts += draft
            draft
        }

        val result = handler.handle(
            CreatePublicationBatchCommand(
                title = "Buffer style post",
                bodyText = "Same text",
                targets = listOf(
                    BatchPublicationTarget(socialAccountId = "acc-li"),
                    BatchPublicationTarget(socialAccountId = "acc-th"),
                ),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        result.status shouldBe BatchPublicationStatus.ALL_CREATED
        result.results.map { it.socialAccountId } shouldBe listOf("acc-li", "acc-th")
        result.results.all { it.publicationId != null } shouldBe true
        createdDrafts.map { it.groupId }.distinct().size shouldBe 1
        transactionCount shouldBe 1
        createdDrafts.all { it.groupId != null } shouldBe true
        result.results.map { it.groupId }.distinct().size shouldBe 1
        result.results.all { it.groupId == createdDrafts.first().groupId } shouldBe true
        coVerify(exactly = 2) { publicationRepository.createDraft(any<PublicationDraft>()) }
    }

    @Test
    fun `keeps valid targets and reports per-target failure when a validator rejects one`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-li") } returns
            linkedInAccount(workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-th") } returns
            threadsAccount(workspaceId)
        coEvery { providerCapabilityValidator.validate(any<ProviderCapabilityValidationInput>()) } coAnswers {
            val input = it.invocation.args[0] as ProviderCapabilityValidationInput
            if (input.provider == SocialProvider.THREADS) {
                throw PublicationValidationException("Threads media not ready")
            }
        }
        coEvery { publicationRepository.createDraft(any<PublicationDraft>()) } coAnswers {
            it.invocation.args[0] as PublicationDraft
        }

        val result = handler.handle(
            CreatePublicationBatchCommand(
                bodyText = "Hi",
                targets = listOf(
                    BatchPublicationTarget(socialAccountId = "acc-li"),
                    BatchPublicationTarget(socialAccountId = "acc-th"),
                ),
                scheduleMode = ScheduleMode.NOW,
            ),
        )

        result.status shouldBe BatchPublicationStatus.PARTIAL
        val byAccount = result.results.associateBy { it.socialAccountId }
        byAccount["acc-li"]?.publicationId?.startsWith("pub-") shouldBe true
        byAccount["acc-th"]?.publicationId shouldBe null
        byAccount["acc-th"]?.errorCode shouldBe "CAPABILITY_VIOLATION"
        coVerify(exactly = 1) { publicationRepository.createDraft(any<PublicationDraft>()) }
    }

    @Test
    fun `propagates persistence failures instead of reporting target validation`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        val failure = IllegalStateException("database unavailable")
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-li") } returns
            linkedInAccount(workspaceId)
        coEvery { publicationRepository.createDraft(any<PublicationDraft>()) } throws failure

        val thrown = runCatching {
            handler.handle(
                CreatePublicationBatchCommand(
                    bodyText = "Hi",
                    targets = listOf(BatchPublicationTarget(socialAccountId = "acc-li")),
                    scheduleMode = ScheduleMode.NOW,
                ),
            )
        }.exceptionOrNull()

        thrown shouldBe failure
        transactionCount shouldBe 1
    }

    @Test
    fun `does not persist valid targets when validation of a later target fails technically`() = runTest {
        val workspaceId = "ws-1"
        val principalId = "u-1"
        val failure = IllegalStateException("provider service unavailable")
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(principalId, PrincipalType.USER, principalId)
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-li") } returns
            linkedInAccount(workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-th") } returns
            threadsAccount(workspaceId)
        every { providerCapabilityValidator.validate(any()) } answers {
            when (val input = invocation.args[0]) {
                is ProviderCapabilityValidationInput ->
                    if (input.provider == SocialProvider.THREADS) throw failure else Unit
                else -> error("Unexpected provider capability input: $input")
            }
        }

        val thrown = runCatching {
            handler.handle(
                CreatePublicationBatchCommand(
                    bodyText = "Hi",
                    targets = listOf(
                        BatchPublicationTarget(socialAccountId = "acc-li"),
                        BatchPublicationTarget(socialAccountId = "acc-th"),
                    ),
                    scheduleMode = ScheduleMode.NOW,
                ),
            )
        }.exceptionOrNull()

        thrown shouldBe failure
        coVerify(exactly = 0) { publicationRepository.createDraft(any<PublicationDraft>()) }
        coVerify(exactly = 0) { publicationJobRepository.enqueue(any()) }
    }

    @Test
    fun `reports all-failed when no target passes validation`() = runTest {
        val workspaceId = "ws-1"
        coEvery { principalContextProvider.require() } returns PrincipalContext("u-1", PrincipalType.USER, "u-1")
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-li") } returns
            linkedInAccount(workspaceId)
        coEvery { socialAccountRepository.findByWorkspaceAndId(workspaceId, "acc-th") } returns
            threadsAccount(workspaceId)
        coEvery { providerCapabilityValidator.validate(any<ProviderCapabilityValidationInput>()) } coAnswers {
            throw PublicationValidationException("noop")
        }
        val result = handler.handle(
            CreatePublicationBatchCommand(
                bodyText = "Hi",
                targets = listOf(
                    BatchPublicationTarget(socialAccountId = "acc-li"),
                    BatchPublicationTarget(socialAccountId = "acc-th"),
                ),
                scheduleMode = ScheduleMode.NOW,
            ),
        )
        result.status shouldBe BatchPublicationStatus.ALL_FAILED
        result.results.all { it.publicationId == null } shouldBe true
    }
}
