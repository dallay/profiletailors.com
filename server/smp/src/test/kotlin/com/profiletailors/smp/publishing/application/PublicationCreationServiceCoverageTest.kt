@file:Suppress("MaxLineLength", "TooManyFunctions", "LongMethod")

package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PublicationCreationServiceCoverageTest {
    private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    private fun createService(
        enabled: Boolean = false,
        mediaResolver: MediaAssetResolver = mockk(relaxed = true),
        publicationAssetRepository: PublicationAssetRepository = mockk(relaxed = true),
        socialAccountRepository: SocialAccountRepository = mockk(relaxed = true),
        publicationRepository: PublicationRepository = mockk(relaxed = true),
        publicationJobRepository: PublicationJobRepository = mockk(relaxed = true),
        providerCapabilityValidator: ProviderCapabilityValidator = mockk(relaxed = true),
    ): Triple<PublicationCreationService, PublicationAssetRepository, MediaAssetResolver> {
        val txn = object : AtomicTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
        }
        val service = PublicationCreationService(
            socialAccountRepository = socialAccountRepository,
            publicationRepository = publicationRepository,
            publicationAssetRepository = publicationAssetRepository,
            publicationJobRepository = publicationJobRepository,
            transactionRunner = txn,
            providerCapabilityValidator = providerCapabilityValidator,
            schedulingPolicy = PublicationSchedulingPolicy(),
            mediaAssetResolver = mediaResolver,
            mediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = enabled),
            clock = clock,
        )
        return Triple(service, publicationAssetRepository, mediaResolver)
    }

    private fun activeAccount(id: String = "acc-1", workspaceId: String = "ws-1"): SocialAccount = SocialAccount(
        id = id,
        socialConnectionId = "conn-1",
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "p-acc-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Account",
        status = SocialConnectionStatus.ACTIVE,
    )

    @Test
    fun `rejects when body and assets empty`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val pubRepo = mockk<PublicationRepository>(relaxed = true)
        val (service, _, _) = createService(
            socialAccountRepository = repo,
            publicationRepository = pubRepo,
        )
        assertThrows<IllegalArgumentException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = null,
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                mediaUrls = emptyList(),
                assetIds = emptyList(),
            )
        }
    }

    @Test
    fun `rejects scheduledFor in past`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val (service, _, _) = createService(socialAccountRepository = repo)
        assertThrows<IllegalArgumentException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = Instant.parse("2025-12-31T12:00:00Z"),
            )
        }
    }

    @Test
    fun `rejects NOW with scheduledFor provided`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val (service, _, _) = createService(socialAccountRepository = repo)
        assertThrows<IllegalArgumentException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                scheduleMode = ScheduleMode.NOW,
            )
        }
    }

    @Test
    fun `rejects SCHEDULED_AT missing scheduledFor`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val (service, _, _) = createService(socialAccountRepository = repo)
        assertThrows<IllegalArgumentException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = null,
                scheduleMode = ScheduleMode.SCHEDULED_AT,
            )
        }
    }

    @Test
    fun `blocks oversized media url`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val (service, _, _) = createService(socialAccountRepository = repo)
        assertThrows<PublicationValidationException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                mediaUrls = listOf("https://cdn.example.com/oversized.jpg"),
            )
        }
    }

    @Test
    fun `blocks too-large media url`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val (service, _, _) = createService(socialAccountRepository = repo)
        assertThrows<PublicationValidationException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                mediaUrls = listOf("https://cdn.example.com/too-large.png"),
            )
        }
    }

    @Test
    fun `blocks disallowed extensions`() = runTest {
        val extensions = listOf(".exe", ".bin", ".sh", ".bat")
        for (ext in extensions) {
            val repo = mockk<SocialAccountRepository>(relaxed = false)
            coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
            val (service, _, _) = createService(socialAccountRepository = repo)
            assertThrows<PublicationValidationException> {
                service.create(
                    workspaceId = "ws-1",
                    principalId = "u-1",
                    socialAccountId = "acc-1",
                    bodyText = "Hello",
                    scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                    mediaUrls = listOf("https://cdn.example.com/file$ext"),
                )
            }
        }
    }

    @Test
    fun `blocks private urls`() = runTest {
        val privateUrls = listOf(
            "http://127.0.0.1/evil.jpg",
            "http://localhost/evil.jpg",
            "http://10.0.0.1/evil.jpg",
            "http://192.168.1.1/evil.jpg",
            "http://169.254.1.1/evil.jpg",
            "http://172.16.0.5/evil.jpg",
            "http://172.31.255.1/evil.jpg",
            "http://0.0.0.0/evil.jpg",
            "http://[::1]/evil.jpg",
            "http://[fc00::1]/evil.jpg",
            "http://[fd00::1]/evil.jpg",
            "http://[fe80::1]/evil.jpg",
            "ftp://cdn.example.com/file.jpg",
            "not-a-url",
        )
        for (url in privateUrls) {
            val repo = mockk<SocialAccountRepository>(relaxed = false)
            coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
            val (service, _, _) = createService(socialAccountRepository = repo)
            assertThrows<PublicationValidationException> {
                service.create(
                    workspaceId = "ws-1",
                    principalId = "u-1",
                    socialAccountId = "acc-1",
                    bodyText = "Hello",
                    scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                    mediaUrls = listOf(url),
                )
            }
        }
    }

    @Test
    fun `does not block 172 32 outside private range`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val (service, _, _) = createService(
            socialAccountRepository = repo,
            publicationRepository = pubRepo,
        )
        val result = service.create(
            workspaceId = "ws-1",
            principalId = "u-1",
            socialAccountId = "acc-1",
            bodyText = "Hello",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            mediaUrls = listOf("http://172.32.0.1/image.jpg"),
        )
        result.bodyText shouldBe "Hello"
    }

    @Test
    fun `infers media types correctly`() = runTest {
        val cases = mapOf(
            "https://cdn.example.com/doc.pdf" to "APPLICATION/PDF",
            "https://cdn.example.com/video.mp4" to "VIDEO/MP4",
            "https://cdn.example.com/video.mov" to "VIDEO/MP4",
            "https://cdn.example.com/image.png" to "IMAGE/PNG",
            "https://cdn.example.com/photo.jpg" to "IMAGE/JPEG",
            "https://cdn.example.com/photo.jpeg" to "IMAGE/JPEG",
            "https://cdn.example.com/anim.gif" to "IMAGE/GIF",
            "https://cdn.example.com/pic.webp" to "IMAGE/WEBP",
            "https://cdn.example.com/unknown.xyz" to "IMAGE/JPEG",
        )
        for ((url, expectedType) in cases) {
            val repo = mockk<SocialAccountRepository>(relaxed = false)
            coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
            val assetRepo = mockk<PublicationAssetRepository>(relaxed = false)
            val slot = slot<PublicationAsset>()
            coEvery { assetRepo.create(capture(slot)) } answers { slot.captured }
            val pubRepo = mockk<PublicationRepository>(relaxed = false)
            coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
            val (service, _, _) = createService(
                socialAccountRepository = repo,
                publicationAssetRepository = assetRepo,
                publicationRepository = pubRepo,
            )
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                mediaUrls = listOf(url),
            )
            slot.captured.mediaType shouldBe expectedType
        }
    }

    @Test
    fun `resolveAssets empty returns early no lookup`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val assetRepo = mockk<PublicationAssetRepository>(relaxed = false)
        coEvery { assetRepo.findByWorkspaceAndIds(any(), any()) } returns emptyList()
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val mediaResolver = mockk<MediaAssetResolver>(relaxed = false)
        coEvery { mediaResolver.resolveReadyAssets(any(), any()) } returns emptyList()
        val (service, _, _) = createService(
            enabled = true,
            socialAccountRepository = repo,
            publicationAssetRepository = assetRepo,
            publicationRepository = pubRepo,
            mediaResolver = mediaResolver,
        )
        val result = service.create(
            workspaceId = "ws-1",
            principalId = "u-1",
            socialAccountId = "acc-1",
            bodyText = "Hello",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            assetIds = emptyList(),
        )
        result.assetIds.isEmpty() shouldBe true
        coVerify(exactly = 0) { assetRepo.findByWorkspaceAndIds(any(), any()) }
        coVerify(exactly = 0) { mediaResolver.resolveReadyAssets(any(), any()) }
    }

    @Test
    fun `resolveAssets external size equals assetIds returns external directly`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val mediaResolver = mockk<MediaAssetResolver>(relaxed = false)
        val assetRepo = mockk<PublicationAssetRepository>(relaxed = false)
        coEvery { assetRepo.create(any()) } answers { it.invocation.args[0] as PublicationAsset }
        val (service, _, _) = createService(
            enabled = true,
            socialAccountRepository = repo,
            publicationAssetRepository = assetRepo,
            publicationRepository = pubRepo,
            mediaResolver = mediaResolver,
        )
        val result = service.create(
            workspaceId = "ws-1",
            principalId = "u-1",
            socialAccountId = "acc-1",
            bodyText = "Hello",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            mediaUrls = listOf("https://cdn.example.com/image.jpg"),
        )
        result.assetIds.size shouldBe 1
        coVerify(exactly = 0) { assetRepo.findByWorkspaceAndIds(any(), any()) }
        coVerify(exactly = 0) { mediaResolver.resolveReadyAssets(any(), any()) }
    }

    @Test
    fun `legacy lookup when media integration disabled`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val assetRepo = mockk<PublicationAssetRepository>(relaxed = false)
        coEvery { assetRepo.findByWorkspaceAndIds(any(), any()) } returns listOf(
            PublicationAsset(
                id = "existing-1",
                workspaceId = "ws-1",
                sourceType = AssetSourceType.UPLOADED,
                mediaType = "IMAGE/JPEG",
                storageKey = "key-1",
                status = com.profiletailors.smp.publishing.domain.PublicationAssetStatus.READY,
                createdByPrincipalId = "u-1",
            ),
        )
        coEvery { assetRepo.create(any()) } answers { it.invocation.args[0] as PublicationAsset }
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val (service, _, _) = createService(
            enabled = false,
            socialAccountRepository = repo,
            publicationAssetRepository = assetRepo,
            publicationRepository = pubRepo,
        )
        val result = service.create(
            workspaceId = "ws-1",
            principalId = "u-1",
            socialAccountId = "acc-1",
            bodyText = "Hello",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            mediaUrls = listOf("https://cdn.example.com/image.jpg"),
            assetIds = listOf("existing-1"),
        )
        result.assetIds.size shouldBe 2
        coVerify { assetRepo.findByWorkspaceAndIds("ws-1", listOf("existing-1")) }
    }

    @Test
    fun `resolve via media when enabled`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val assetRepo = mockk<PublicationAssetRepository>(relaxed = false)
        coEvery { assetRepo.create(any()) } answers { it.invocation.args[0] as PublicationAsset }
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val mediaResolver = mockk<MediaAssetResolver>(relaxed = false)
        coEvery { mediaResolver.resolveReadyAssets("ws-1", listOf("asset-1")) } returns listOf(
            ResolvedAssetSummary(
                assetId = "asset-1",
                workspaceId = "ws-1",
                storageKey = "key-1",
                mediaType = "IMAGE/JPEG",
            ),
        )
        val (service, _, _) = createService(
            enabled = true,
            socialAccountRepository = repo,
            publicationAssetRepository = assetRepo,
            publicationRepository = pubRepo,
            mediaResolver = mediaResolver,
        )
        val result = service.create(
            workspaceId = "ws-1",
            principalId = "u-1",
            socialAccountId = "acc-1",
            bodyText = "Hello",
            scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
            assetIds = listOf("asset-1"),
        )
        result.assetIds shouldBe listOf("asset-1")
        coVerify { mediaResolver.resolveReadyAssets("ws-1", listOf("asset-1")) }
    }

    @Test
    fun `resolve via media timeout throws`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val mediaResolver = mockk<MediaAssetResolver>(relaxed = false)
        coEvery { mediaResolver.resolveReadyAssets(any(), any()) } coAnswers {
            delay(6_000)
            emptyList()
        }
        val (service, _, _) = createService(
            enabled = true,
            socialAccountRepository = repo,
            publicationRepository = pubRepo,
            mediaResolver = mediaResolver,
        )
        assertThrows<MediaServiceUnavailableException> {
            service.create(
                workspaceId = "ws-1",
                principalId = "u-1",
                socialAccountId = "acc-1",
                bodyText = "Hello",
                scheduledFor = Instant.parse("2026-02-01T12:00:00Z"),
                assetIds = listOf("asset-timeout"),
            )
        }
    }

    @Test
    fun `priority and NEXT_SLOT handling`() = runTest {
        val repo = mockk<SocialAccountRepository>(relaxed = false)
        coEvery { repo.findByWorkspaceAndId(any(), any()) } returns activeAccount()
        val pubRepo = mockk<PublicationRepository>(relaxed = false)
        coEvery { pubRepo.createDraft(any()) } answers { it.invocation.args[0] as PublicationDraft }
        val (service, _, _) = createService(socialAccountRepository = repo, publicationRepository = pubRepo)
        val result = service.create(
            workspaceId = "ws-1",
            principalId = "u-1",
            socialAccountId = "acc-1",
            bodyText = "Priority post",
            scheduledFor = null,
            scheduleMode = ScheduleMode.NEXT_SLOT,
            priority = true,
        )
        result.priority shouldBe true
        result.scheduleMode shouldBe ScheduleMode.NEXT_SLOT
    }
}
