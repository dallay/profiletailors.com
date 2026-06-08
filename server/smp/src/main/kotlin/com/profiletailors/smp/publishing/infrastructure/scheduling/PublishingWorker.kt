package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialPublisher
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PublishingJobExecutor(
    private val publicationJobRepository: PublicationJobRepository,
    private val publicationRepository: PublicationRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
    private val deliveryAttemptRepository: DeliveryAttemptRepository,
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val socialPublisher: SocialPublisher,
    private val retryPolicy: DeliveryRetryPolicy,
    private val clock: Clock,
) {
    suspend fun executeClaim(claim: PublicationJobClaim) {
        val now = clock.instant()
        val publication = publicationRepository.findByWorkspaceAndId(
            claim.workspaceId,
            claim.publicationId
        ) ?: error("Publication '${claim.publicationId}' not found for worker claim.")
        
        val socialAccount = socialAccountRepository.findByWorkspaceAndId(
            publication.workspaceId,
            publication.socialAccountId
        ) ?: error("Social account '${publication.socialAccountId}' not found.")
        
        val assets = publicationAssetRepository.findByWorkspaceAndIds(
            publication.workspaceId,
            publication.assetIds
        )

        try {
            validateAndPublish(claim, publication, socialAccount, assets, now)
        } catch (exception: RetryablePublishingException) {
            handlePublishFailure(claim, publication, exception, now)
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            handlePublishFailure(claim, publication, exception, now)
        }
    }

    private suspend fun validateAndPublish(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        socialAccount: com.profiletailors.smp.publishing.domain.SocialAccount,
        assets: List<com.profiletailors.smp.publishing.domain.PublicationAsset>,
        now: Instant
    ) {
        providerCapabilityValidator.validate(
            ProviderCapabilityValidationInput(
                provider = socialAccount.provider,
                socialAccount = socialAccount,
                publication = publication,
                assets = assets,
            ),
        )
        val result = socialPublisher.publish(
            ProviderPublishCommand(
                publicationId = publication.id,
                workspaceId = publication.workspaceId,
                socialAccount = socialAccount,
                publication = publication,
                assets = assets,
            ),
        )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-${UUID.randomUUID()}",
                publicationId = publication.id,
                publicationJobId = claim.jobId,
                attemptNumber = claim.attemptNumber,
                outcome = DeliveryAttemptOutcome.SUCCEEDED,
                retryable = false,
                providerMessage = result.providerMessage,
                externalPublicationId = result.externalPublicationId,
                attemptedAt = now,
            ),
        )
        publicationRepository.markPublished(publication.id, result.externalPublicationId, now)
        publicationJobRepository.complete(claim.jobId, now)
    }

    private suspend fun handlePublishFailure(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        exception: Exception,
        now: Instant
    ) {
        val retryable = exception is RetryablePublishingException
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-${UUID.randomUUID()}",
                publicationId = publication.id,
                publicationJobId = claim.jobId,
                attemptNumber = claim.attemptNumber,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = retryable,
                providerMessage = exception.message,
                providerErrorCode = exception::class.simpleName,
                attemptedAt = now,
            ),
        )
        if (retryPolicy.shouldRetry(claim.attemptNumber, retryable)) {
            publicationJobRepository.rescheduleRetry(
                claim.jobId,
                retryPolicy.nextRetryAt(now),
                claim.attemptNumber
            )
        } else {
            publicationRepository.markFailed(
                publication.id,
                now,
                exception::class.simpleName,
                exception.message
            )
            publicationJobRepository.fail(claim.jobId, now)
        }
    }
}

class RetryablePublishingException(
    message: String,
) : RuntimeException(message)

class PublishingWorker(
    private val publicationJobRepository: PublicationJobRepository,
    private val executor: PublishingJobExecutor,
    private val clock: Clock,
    private val workerId: String,
) {
    suspend fun pollOnce(): PublicationJobClaim? {
        val claim = publicationJobRepository.claimNextDue(clock.instant(), workerId) ?: return null
        executor.executeClaim(claim)
        return claim
    }
}

class PublishingWorkerLifecycle(
    private val enabled: Boolean,
    private val pollInterval: Duration,
    private val taskScheduler: TaskScheduler,
    private val worker: PublishingWorker,
) {
    fun start() {
        if (!enabled) return
        taskScheduler.scheduleAtFixedRate(
            { runBlocking { worker.pollOnce() } },
            pollInterval,
        )
    }
}
