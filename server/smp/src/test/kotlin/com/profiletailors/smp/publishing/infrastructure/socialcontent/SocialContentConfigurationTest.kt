package com.profiletailors.smp.publishing.infrastructure.socialcontent

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialContentBatchWriter
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SocialContentConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(SocialContentConfiguration::class.java)
        .withBean(SocialContentPostRepository::class.java, { mockk(relaxed = true) })
        .withBean(SocialContentCheckpointRepository::class.java, { mockk(relaxed = true) })
        .withBean(AtomicTransactionRunner::class.java, { mockk(relaxed = true) })

    @Test
    fun `registers SocialContentProperties and SocialContentBatchWriter`() {
        contextRunner.run { context ->
            assertNotNull(context.getBean(SocialContentProperties::class.java))
            val batchWriter = context.getBean(SocialContentBatchWriter::class.java)
            assertIs<R2dbcSocialContentBatchWriter>(batchWriter)
        }
    }

    @Test
    fun `social content properties default to disabled and fail closed`() {
        contextRunner.run { context ->
            val properties = context.getBean(SocialContentProperties::class.java)
            assertFalse(properties.discoveryEnabled, "Discovery must default to disabled")
            assertFalse(properties.importEnabled, "Import must default to disabled")
            assertFalse(properties.inboxEnabled, "Inbox must default to disabled")
            assertFalse(properties.repliesEnabled, "Replies must default to disabled")
            assertFalse(properties.syncEnabled, "Sync must default to disabled")
        }
    }

    @Test
    fun `disabled operations do not register external transport or credential beans in social content context`() {
        contextRunner.run { context ->
            assertFalse(
                context.containsBean("linkedInHttpTransport"),
                "Social content configuration must not register external HTTP transport",
            )
            assertFalse(
                context.containsBean("linkedInCredentialGateway"),
                "Social content configuration must not resolve credentials for disabled community operations",
            )
        }
    }
}
