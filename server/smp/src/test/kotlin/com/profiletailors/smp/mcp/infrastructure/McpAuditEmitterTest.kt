package com.profiletailors.smp.mcp.infrastructure

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

@Tag("fast")
class McpAuditEmitterTest {

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun attachAppender() {
        logger = LoggerFactory.getLogger(McpAuditEmitter.AUDIT_LOGGER_NAME) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        logger.level = Level.INFO
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    @Test
    fun `emits a structured log entry on a SUCCESS outcome`() {
        val emitter = McpAuditEmitter()
        val fact = McpToolInvocationAuditFact(
            toolName = "list_channels",
            scopeChecked = "mcp:channels:read",
            grantedScopes = setOf("mcp:channels:read"),
            workspaceId = "ws-1",
            correlationId = "corr-1",
            outcome = McpToolInvocationOutcome.SUCCESS,
        )

        emitter.emit(fact)

        val events = appender.list
        assertThat(events).hasSize(1)
        val message = events.first().formattedMessage
        assertThat(message).contains("mcp.audit.correlation=corr-1")
        assertThat(message).contains("list_channels")
        assertThat(message).contains("SUCCESS")
    }

    @Test
    fun `emits a structured log entry on a DENIED outcome`() {
        val emitter = McpAuditEmitter()
        val fact = McpToolInvocationAuditFact(
            toolName = "create_publication",
            scopeChecked = "mcp:publications:write",
            grantedScopes = setOf("mcp:publications:read"),
            workspaceId = "ws-1",
            correlationId = "corr-2",
            outcome = McpToolInvocationOutcome.DENIED,
        )

        emitter.emit(fact)

        val events = appender.list
        assertThat(events).hasSize(1)
        assertThat(events.first().formattedMessage).contains("DENIED")
    }

    @Test
    fun `emits a structured log entry on an ERROR outcome`() {
        val emitter = McpAuditEmitter()
        val fact = McpToolInvocationAuditFact(
            toolName = "list_publications",
            scopeChecked = "mcp:publications:read",
            grantedScopes = setOf("mcp:publications:read"),
            workspaceId = "ws-1",
            correlationId = "corr-3",
            outcome = McpToolInvocationOutcome.ERROR,
        )

        emitter.emit(fact)

        val events = appender.list
        assertThat(events).hasSize(1)
        assertThat(events.first().formattedMessage).contains("ERROR")
    }

    @Test
    fun `publishes publicationId on write tool success`() {
        val emitter = McpAuditEmitter()
        val fact = McpToolInvocationAuditFact(
            toolName = "create_publication",
            scopeChecked = "mcp:publications:write",
            grantedScopes = setOf("mcp:publications:write"),
            workspaceId = "ws-1",
            correlationId = "corr-4",
            outcome = McpToolInvocationOutcome.SUCCESS,
            publicationId = "pub-X",
        )

        emitter.emit(fact)

        val message = appender.list.first().formattedMessage
        assertThat(message).contains("pub-X")
    }

    @Test
    fun `emit failures are logged at WARN and never propagate`() {
        val emitter = McpAuditEmitter()
        @Suppress("UNCHECKED_CAST")
        logger.addAppender(appender)
        val fact = McpToolInvocationAuditFact(
            toolName = "list_publications",
            scopeChecked = "mcp:publications:read",
            grantedScopes = setOf("mcp:publications:read"),
            workspaceId = "ws-1",
            correlationId = "corr-5",
            outcome = McpToolInvocationOutcome.SUCCESS,
        )

        emitter.emit(fact)
        assertThat(appender.list).hasSize(1)
    }
}
