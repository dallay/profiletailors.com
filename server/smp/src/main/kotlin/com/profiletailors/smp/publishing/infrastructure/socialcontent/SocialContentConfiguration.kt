package com.profiletailors.smp.publishing.infrastructure.socialcontent

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialContentBatchWriter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SocialContentProperties::class)
class SocialContentConfiguration {
    @Bean
    fun socialContentBatchWriter(
        postRepository: SocialContentPostRepository,
        checkpointRepository: SocialContentCheckpointRepository,
        transactionRunner: AtomicTransactionRunner,
    ): SocialContentBatchWriter = R2dbcSocialContentBatchWriter(
        postRepository = postRepository,
        checkpointRepository = checkpointRepository,
        transactionRunner = transactionRunner,
    )
}
