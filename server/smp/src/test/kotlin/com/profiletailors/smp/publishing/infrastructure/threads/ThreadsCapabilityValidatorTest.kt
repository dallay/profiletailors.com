package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.throwable.shouldHaveMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ThreadsCapabilityValidatorTest {
    private val threadsAccount = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.THREADS,
        providerAccountId = "threads-user-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Threads user",
        status = SocialConnectionStatus.ACTIVE,
    )
    private val organizationAccount = threadsAccount.copy(
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Threads org",
    )
    private val validator = ThreadsCapabilityValidator()

    @Test
    fun `rejects non-threads provider`() {
        val linkedInAccount = threadsAccount.copy(provider = SocialProvider.LINKEDIN)
        val exception = assertThrows<IllegalArgumentException> {
            validator.validate(
                input(
                    socialAccount = linkedInAccount,
                    provider = SocialProvider.LINKEDIN,
                    assets = emptyList(),
                    body = "hi",
                ),
            )
        }
        exception shouldHaveMessage "Threads capability validator only supports THREADS."
    }

    @Test
    fun `rejects organization pages`() {
        val exception = assertThrows<IllegalArgumentException> {
            validator.validate(input(organizationAccount, assets = emptyList(), body = "hi"))
        }
        exception shouldHaveMessage "Threads publishing supports personal profiles only."
    }

    @Test
    fun `rejects empty body with no assets as unsupported content`() {
        val exception = assertThrows<PublicationValidationException> {
            validator.validate(input(threadsAccount, assets = emptyList(), body = ""))
        }
        exception shouldHaveMessage "Unsupported Threads publication content."
    }

    @Test
    fun `rejects media assets that are not ready`() {
        val exception = assertThrows<PublicationValidationException> {
            validator.validate(
                input(threadsAccount, assets = listOf(asset("image/jpeg", ready = false)), body = null),
            )
        }
        exception shouldHaveMessage "Threads media must be ready before publishing."
    }

    @Test
    fun `rejects oversized carousel of more than 20 assets`() {
        val exception = assertThrows<PublicationValidationException> {
            validator.validate(
                input(threadsAccount, assets = (1..21).map { asset("image/jpeg") }, body = ""),
            )
        }
        exception shouldHaveMessage "Unsupported Threads publication content."
    }

    @Test
    fun `rejects unsupported media type in carousel assets`() {
        val exception = assertThrows<PublicationValidationException> {
            validator.validate(
                input(
                    threadsAccount,
                    assets = listOf(asset("audio/mpeg"), asset("audio/mpeg")),
                    body = "hi",
                ),
            )
        }
        exception shouldHaveMessage "Unsupported Threads media type."
    }

    @Test
    fun `accepts text-only publication`() {
        validator.validate(input(threadsAccount, assets = emptyList(), body = "hello"))
    }

    @Test
    fun `accepts single image`() {
        validator.validate(input(threadsAccount, assets = listOf(asset("image/jpeg")), body = null))
    }

    @Test
    fun `accepts single video`() {
        validator.validate(input(threadsAccount, assets = listOf(asset("video/mp4")), body = null))
    }

    @Test
    fun `accepts two-image carousel`() {
        validator.validate(
            input(threadsAccount, assets = listOf(asset("image/jpeg"), asset("image/jpeg")), body = ""),
        )
    }

    private fun input(
        socialAccount: SocialAccount = threadsAccount,
        provider: SocialProvider = SocialProvider.THREADS,
        assets: List<PublicationAsset>,
        body: String?,
    ) = ProviderCapabilityValidationInput(
        provider = provider,
        socialAccount = socialAccount,
        publication = PublicationDraft(
            id = "publication-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = provider,
            socialAccountId = socialAccount.id,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = body,
        ),
        assets = assets,
    )

    private fun asset(mediaType: String, ready: Boolean = true) = PublicationAsset(
        id = "asset-${mediaType.replace('/', '-')}",
        workspaceId = "workspace-1",
        sourceType = AssetSourceType.UPLOADED,
        mediaType = mediaType,
        storageKey = "media/key",
        status = if (ready) PublicationAssetStatus.READY else PublicationAssetStatus.PROCESSING,
        createdByPrincipalId = "principal-1",
    )
}
