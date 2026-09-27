package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class ThreadsPublishingContractTest {
    private val account = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.THREADS,
        providerAccountId = "threads-user-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Threads user",
        status = SocialConnectionStatus.ACTIVE,
    )

    @Test
    fun `threads accepts text image video and bounded carousel`() {
        val validator = ThreadsCapabilityValidator()

        validator.validate(input(body = "text", assets = emptyList()))
        validator.validate(input(assets = listOf(asset("image/jpeg"))))
        validator.validate(input(assets = listOf(asset("video/mp4"))))
        validator.validate(input(assets = (1..20).map { asset(if (it % 2 == 0) "image/jpeg" else "video/mp4") }))
    }

    @Test
    fun `threads rejects unsupported content before provider io`() {
        val validator = ThreadsCapabilityValidator()

        shouldThrow<RuntimeException> { validator.validate(input(body = "text", assets = listOf(asset("audio/mpeg")))) }
        shouldThrow<RuntimeException> {
            validator.validate(input(body = "", assets = (1..21).map { asset("image/jpeg") }))
        }
        shouldThrow<RuntimeException> { validator.validate(input(body = "", assets = emptyList())) }
    }

    private fun input(body: String? = null, assets: List<PublicationAsset>) = ProviderCapabilityValidationInput(
        provider = SocialProvider.THREADS,
        socialAccount = account,
        publication = PublicationDraft(
            id = "publication-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.THREADS,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = body,
        ),
        assets = assets,
    )

    private fun asset(mediaType: String) = PublicationAsset(
        id = "asset-${mediaType.replace('/', '-')}",
        workspaceId = "workspace-1",
        sourceType = AssetSourceType.UPLOADED,
        mediaType = mediaType,
        storageKey = "media/key",
        status = PublicationAssetStatus.READY,
        createdByPrincipalId = "principal-1",
    )
}
