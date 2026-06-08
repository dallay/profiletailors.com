package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
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
class PublishingSchedulingConfiguration {
    @Bean
    fun publishingTaskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 1
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
        providerCapabilityValidator: ProviderCapabilityValidator,
        socialPublisher: SocialPublisher,
        publishingRetryPolicy: DeliveryRetryPolicy,
        clock: Clock,
    ): PublishingJobExecutor = PublishingJobExecutor(
        publicationJobRepository = publicationJobRepository,
        publicationRepository = publicationRepository,
        socialAccountRepository = socialAccountRepository,
        publicationAssetRepository = publicationAssetRepository,
        deliveryAttemptRepository = deliveryAttemptRepository,
        providerCapabilityValidator = providerCapabilityValidator,
        socialPublisher = socialPublisher,
        retryPolicy = publishingRetryPolicy,
        clock = clock,
    )

    @Bean
    fun publishingWorker(
        publicationJobRepository: PublicationJobRepository,
        publishingJobExecutor: PublishingJobExecutor,
        clock: Clock,
    ): PublishingWorker = PublishingWorker(
        publicationJobRepository = publicationJobRepository,
        executor = publishingJobExecutor,
        clock = clock,
        workerId = "worker-${UUID.randomUUID()}",
    )

    @Bean
    fun publishingWorkerLifecycle(
        @Value("\${publishing.worker.enabled:false}") enabled: Boolean,
        @Value("\${publishing.worker.poll-interval:PT30S}") pollInterval: Duration,
        publishingTaskScheduler: TaskScheduler,
        publishingWorker: PublishingWorker,
    ): PublishingWorkerLifecycle = PublishingWorkerLifecycle(
        enabled = enabled,
        pollInterval = pollInterval,
        taskScheduler = publishingTaskScheduler,
        worker = publishingWorker,
    ).also { it.start() }
}
