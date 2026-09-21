package com.profiletailors.smp.mcp.infrastructure

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.common.domain.Service
import org.slf4j.LoggerFactory

@Service
open class McpAuditEmitter(private val objectMapper: ObjectMapper = ObjectMapper()) {

    private val logger = LoggerFactory.getLogger(AUDIT_LOGGER_NAME)

    /** Serializes and records an MCP tool invocation audit [fact]. */
    open fun emit(fact: McpToolInvocationAuditFact) {
        try {
            val payload = objectMapper.writeValueAsString(fact.toMap())
            val marker = "mcp.audit.correlation=${fact.correlationId}"
            logger.info("$marker $payload")
        } catch (ex: JsonProcessingException) {
            logger.warn(
                "mcp.audit-emit-failed tool={} correlation={}",
                fact.toolName,
                fact.correlationId,
                ex,
            )
        } catch (ex: IllegalArgumentException) {
            logger.warn(
                "mcp.audit-emit-failed tool={} correlation={}",
                fact.toolName,
                fact.correlationId,
                ex,
            )
        }
    }

    fun success(fact: McpToolInvocationAuditFact): McpAuditEmitter = apply { emit(fact) }
    fun denied(fact: McpToolInvocationAuditFact): McpAuditEmitter = apply { emit(fact) }
    fun error(fact: McpToolInvocationAuditFact): McpAuditEmitter = apply { emit(fact) }

    companion object {
        const val AUDIT_LOGGER_NAME: String = "mcp.audit"
    }
}
