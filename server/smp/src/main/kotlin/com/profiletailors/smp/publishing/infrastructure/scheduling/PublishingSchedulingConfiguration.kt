package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.SocialPublisher
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
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
    private val mediaAssetResolver: MediaAssetResolver,
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
        properties: PublishingWorkerProperties,
    ): DeliveryRetryPolicy = DeliveryRetryPolicy(
        maxRetries = properties.maxRetries,
        retryBackoff = properties.retryBackoff,
    )

    @Bean
    fun publishingJobExecutor(
        notificationEventRepository: NotificationEventRepository?,
        publishingRetryPolicy: DeliveryRetryPolicy,
    ): PublishingJobExecutor = PublishingJobExecutor(
        publicationJobRepository = publicationJobRepository,
        publicationRepository = publicationRepository,
        socialAccountRepository = socialAccountRepository,
        mediaAssetResolver = mediaAssetResolver,
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
        properties: PublishingWorkerProperties,
        publishingTaskScheduler: TaskScheduler,
        publishingWorker: PublishingWorker,
    ): PublishingWorkerLifecycle = PublishingWorkerLifecycle(
        enabled = properties.enabled,
        pollInterval = properties.pollInterval,
        blockedRecoveryInterval = properties.blockedRecoveryInterval,
        taskScheduler = publishingTaskScheduler,
        worker = publishingWorker,
    ).also { it.start() }
}
