package com.profiletailors.smp.publishing.infrastructure.scheduling

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class PublishingLifecycleLoggerTest {
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(PublishingLifecycleLogger::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `claimed emits required schema at info`() {
        PublishingLifecycleLogger().claimed(
            publicationId = "pub-1",
            jobId = "job-1",
            workspaceId = "workspace-1",
            attemptNumber = 2,
            provider = SocialProvider.LINKEDIN,
        )

        assertCommonFields(appender.list.single(), Level.INFO, "publishing_attempt_claimed")
    }

    @Test
    fun `succeeded emits required schema at info`() {
        PublishingLifecycleLogger().succeeded(
            publicationId = "pub-1",
            jobId = "job-1",
            workspaceId = "workspace-1",
            attemptNumber = 2,
            provider = SocialProvider.LINKEDIN,
            durationMs = 15,
        )

        val event = appender.list.single()
        assertCommonFields(event, Level.INFO, "publishing_attempt_succeeded")
        event.formattedMessage shouldContain "outcome=SUCCEEDED"
        event.formattedMessage shouldContain "durationMs=15"
    }

    @Test
    fun `retry scheduled emits required schema at warn`() {
        PublishingLifecycleLogger().retryScheduled(
            publicationId = "pub-1",
            jobId = "job-1",
            workspaceId = "workspace-1",
            attemptNumber = 2,
            provider = SocialProvider.LINKEDIN,
            failureCategory = PublishingFailureCategory.PROVIDER_UNAVAILABLE,
            durationMs = 15,
        )

        val event = appender.list.single()
        assertCommonFields(event, Level.WARN, "publishing_retry_scheduled")
        event.formattedMessage shouldContain "outcome=FAILED"
        event.formattedMessage shouldContain "failureCategory=PROVIDER_UNAVAILABLE"
        event.formattedMessage shouldContain "retryable=true"
        event.formattedMessage shouldContain "durationMs=15"
    }

    @Test
    fun `blocked emits required schema at warn`() {
        PublishingLifecycleLogger().blocked(
            publicationId = "pub-1",
            jobId = "job-1",
            workspaceId = "workspace-1",
            attemptNumber = 2,
            provider = SocialProvider.LINKEDIN,
            failureCategory = PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED,
            durationMs = 15,
        )

        val event = appender.list.single()
        assertCommonFields(event, Level.WARN, "publishing_blocked")
        event.formattedMessage shouldContain "outcome=BLOCKED"
        event.formattedMessage shouldContain "failureCategory=ACCOUNT_RECONNECT_REQUIRED"
        event.formattedMessage shouldContain "retryable=false"
        event.formattedMessage shouldContain "durationMs=15"
    }

    @Test
    fun `terminal failure emits required schema at error`() {
        PublishingLifecycleLogger().terminalFailure(
            publicationId = "pub-1",
            jobId = "job-1",
            workspaceId = "workspace-1",
            attemptNumber = 2,
            provider = SocialProvider.LINKEDIN,
            failureCategory = PublishingFailureCategory.PROVIDER_VALIDATION_FAILED,
            durationMs = -1,
        )

        val event = appender.list.single()
        assertCommonFields(event, Level.ERROR, "publishing_terminal_failure")
        event.formattedMessage shouldContain "outcome=FAILED"
        event.formattedMessage shouldContain "failureCategory=PROVIDER_VALIDATION_FAILED"
        event.formattedMessage shouldContain "retryable=false"
        event.formattedMessage shouldContain "durationMs=0"
    }

    private fun assertCommonFields(event: ILoggingEvent, level: Level, eventName: String) {
        event.level shouldBe level
        event.formattedMessage shouldContain "event=$eventName"
        event.formattedMessage shouldContain "publicationId=pub-1"
        event.formattedMessage shouldContain "jobId=job-1"
        event.formattedMessage shouldContain "workspaceId=workspace-1"
        event.formattedMessage shouldContain "attemptNumber=2"
        event.formattedMessage shouldContain "provider=LINKEDIN"
    }
}
