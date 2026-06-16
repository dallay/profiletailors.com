package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.SocialPublisher
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Configuration
class PublishingSchedulingConfiguration(
    private val publicationJobRepository: PublicationJobRepository,
    private val publicationRepository: PublicationRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
    private val deliveryAttemptRepository: DeliveryAttemptRepository,
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val socialPublisher: SocialPublisher,
    private val clock: Clock,
) {
    @Bean
    fun publishingTaskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("publishing-worker-")
        initialize()
    }

    @Bean
    fun publicationSchedulingPolicy(): PublicationSchedulingPolicy = PublicationSchedulingPolicy()

    @Bean
    fun publishingRetryPolicy(
        @Value("\${publishing.worker.max-retries:3}") maxRetries: Int,
        @Value("\${publishing.worker.retry-backoff:PT5M}") retryBackoff: Duration,
    ): DeliveryRetryPolicy = DeliveryRetryPolicy(maxRetries = maxRetries, retryBackoff = retryBackoff)

    @Bean
    fun publishingJobExecutor(
        publicationJobRepository: PublicationJobRepository,
        publicationRepository: PublicationRepository,
        socialAccountRepository: SocialAccountRepository,
        publicationAssetRepository: PublicationAssetRepository,
        deliveryAttemptRepository: DeliveryAttemptRepository,
        notificationEventRepository: NotificationEventRepository?,
        providerCapabilityValidator: ProviderCapabilityValidator,
        socialPublisher: SocialPublisher,
        publishingRetryPolicy: DeliveryRetryPolicy,
    ): PublishingJobExecutor = PublishingJobExecutor(
        publicationJobRepository = publicationJobRepository,
        publicationRepository = publicationRepository,
        socialAccountRepository = socialAccountRepository,
        publicationAssetRepository = publicationAssetRepository,
        deliveryAttemptRepository = deliveryAttemptRepository,
        notificationEventRepository = notificationEventRepository,
        providerCapabilityValidator = providerCapabilityValidator,
        socialPublisher = socialPublisher,
        retryPolicy = publishingRetryPolicy,
        clock = clock,
    )

    @Bean
    fun publishingWorker(
        publicationJobRepository: PublicationJobRepository,
        publicationRepository: PublicationRepository,
        publishingJobExecutor: PublishingJobExecutor,
    ): PublishingWorker = PublishingWorker(
        publicationJobRepository = publicationJobRepository,
        publicationRepository = publicationRepository,
        executor = publishingJobExecutor,
        clock = clock,
        workerId = "worker-${UUID.randomUUID()}",
    )

    @Bean
    fun publishingWorkerLifecycle(
        @Value("\${publishing.worker.enabled:false}") enabled: Boolean,
        @Value("\${publishing.worker.poll-interval:PT30S}") pollInterval: Duration,
        @Value("\${publishing.worker.blocked-recovery-interval:PT5M}") blockedRecoveryInterval: Duration,
        publishingTaskScheduler: TaskScheduler,
        publishingWorker: PublishingWorker,
    ): PublishingWorkerLifecycle = PublishingWorkerLifecycle(
        enabled = enabled,
        pollInterval = pollInterval,
        blockedRecoveryInterval = blockedRecoveryInterval,
        taskScheduler = publishingTaskScheduler,
        worker = publishingWorker,
    ).also { it.start() }
}
