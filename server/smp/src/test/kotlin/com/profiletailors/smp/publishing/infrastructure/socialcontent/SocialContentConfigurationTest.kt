package com.profiletailors.smp.publishing.infrastructure.socialcontent

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialContentBatchWriter
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class SocialContentConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(SocialContentConfiguration::class.java)
        .withBean(SocialContentPostRepository::class.java, { mockk(relaxed = true) })
        .withBean(SocialContentCheckpointRepository::class.java, { mockk(relaxed = true) })
        .withBean(AtomicTransactionRunner::class.java, { mockk(relaxed = true) })

    @Test
    fun `registers SocialContentProperties and SocialContentBatchWriter`() {
        contextRunner.run { context ->
            context.getBean(SocialContentProperties::class.java).shouldNotBeNull()
            context.getBean(SocialContentBatchWriter::class.java).shouldBeInstanceOf<R2dbcSocialContentBatchWriter>()
        }
    }

    @Test
    fun `social content properties default to disabled and fail closed`() {
        contextRunner.run { context ->
            val properties = context.getBean(SocialContentProperties::class.java)
            properties.discoveryEnabled.shouldBeFalse()
            properties.importEnabled.shouldBeFalse()
            properties.inboxEnabled.shouldBeFalse()
            properties.repliesEnabled.shouldBeFalse()
            properties.syncEnabled.shouldBeFalse()
        }
    }

    @Test
    fun `disabled operations do not register external transport or credential beans in social content context`() {
        contextRunner.run { context ->
            context.containsBean("linkedInHttpTransport") shouldBe false
            context.containsBean("linkedInCredentialGateway") shouldBe false
        }
    }
}
