package com.profiletailors.smp.governance.domain

import java.time.Instant
import java.util.Base64

data class AuditEventPage(
    val cursor: String?,
    val limit: Int,
    val returned: Int,
    val hasMore: Boolean,
    val nextCursor: String?,
)

data class AuditEventCursor(
    val createdAt: Instant,
    val id: String,
)

class InvalidAuditEventCursorException(cause: Throwable? = null) :
    IllegalArgumentException("Invalid audit cursor", cause)

object AuditEventCursorCodec {
    fun encode(cursor: AuditEventCursor): String {
        val payload = "${cursor.createdAt}|${cursor.id}"
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    @Suppress("ThrowsCount")
    fun decode(value: String): AuditEventCursor {
        val token = value.trim()
        if (token.isBlank()) throw InvalidAuditEventCursorException()

        return try {
            val decoded = String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
            val separatorIndex = decoded.indexOf('|')
            if (separatorIndex <= 0 || separatorIndex >= decoded.lastIndex) {
                throw InvalidAuditEventCursorException()
            }
            val createdAt = Instant.parse(decoded.substring(0, separatorIndex))
            val id = decoded.substring(separatorIndex + 1)
            if (id.isBlank()) throw InvalidAuditEventCursorException()
            AuditEventCursor(createdAt = createdAt, id = id)
        } catch (exception: InvalidAuditEventCursorException) {
            throw exception
        } catch (exception: IllegalArgumentException) {
            throw InvalidAuditEventCursorException(exception)
        } catch (exception: java.time.format.DateTimeParseException) {
            throw InvalidAuditEventCursorException(exception)
        }
    }
}

data class AuditEventItem(
    val id: String,
    val eventType: String,
    val action: String?,
    val requestName: String?,
    val requestPath: String?,
    val permission: String?,
    val actorPrincipalId: String?,
    val workspaceId: String?,
    val targetType: String?,
    val targetId: String?,
    val outcome: String?,
    val reasonCode: String?,
    val roleKeys: List<String>,
    val details: Map<String, String>,
    val createdAt: Instant,
)

interface AuditEventReader {
    suspend fun readWorkspaceEvents(
        workspaceId: String,
        targetType: String? = null,
        action: String? = null,
        eventType: String? = null,
        actorPrincipalId: String? = null,
        createdAfter: Instant? = null,
        createdBefore: Instant? = null,
        cursor: AuditEventCursor? = null,
        limit: Int = 50,
    ): List<AuditEventItem>
}
