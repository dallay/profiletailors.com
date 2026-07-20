package com.profiletailors.smp.notifications.infrastructure.persistence

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * R2DBC-backed [NotificationRepository].
 *
 * Schema (see [com.profiletailors.smp.notifications.infrastructure.persistence.NotificationsSchemaInitializer]):
 * <pre>
 * CREATE TABLE notifications (
 *   id                VARCHAR(255) PRIMARY KEY,
 *   idempotency_key   VARCHAR(500) UNIQUE NOT NULL,
 *   channel           VARCHAR(50)  NOT NULL,
 *   recipient         VARCHAR(500) NOT NULL,
 *   template_id       VARCHAR(255) NOT NULL,
 *   payload           JSONB        NOT NULL,
 *   status            VARCHAR(50)  NOT NULL,
 *   sent_at           TIMESTAMP,
 *   failed_at         TIMESTAMP,
 *   error_message     TEXT,
 *   created_at        TIMESTAMP    NOT NULL,
 *   updated_at        TIMESTAMP    NOT NULL
 * );
 * </pre>
 */
@Repository
internal class R2dbcNotificationRepository(private val databaseClient: DatabaseClient) : NotificationRepository {

    override suspend fun save(notification: Notification): Notification = try {
        databaseClient.sql(INSERT_SQL)
            .bind("id", notification.id.value)
            .bind("idempotencyKey", notification.idempotencyKey.value)
            .bind("channel", notification.channel.name)
            .bind("recipient", notification.recipient.value)
            .bind("templateId", notification.templateId.value)
            .bind("payload", notification.payload.variables.toJsonb())
            .bind("status", notification.status.name)
            .bindNullable("sentAt", notification.sentAt?.toOffsetDateTime(), OffsetDateTime::class.java)
            .bindNullable("failedAt", notification.failedAt?.toOffsetDateTime(), OffsetDateTime::class.java)
            .bindNullable("errorMessage", notification.errorMessage, String::class.java)
            .bind("createdAt", notification.createdAt.toOffsetDateTime())
            .bind("updatedAt", notification.updatedAt.toOffsetDateTime())
            .fetch()
            .rowsUpdated()
            .asFlow()
            .let { it.firstOrNull() }
            .let { notification }
    } catch (duplicate: DuplicateKeyException) {
        throw DuplicateNotificationException(notification.idempotencyKey, duplicate)
    }

    override suspend fun update(notification: Notification): Notification {
        databaseClient.sql(UPDATE_SQL)
            .bind("status", notification.status.name)
            .bindNullable("sentAt", notification.sentAt?.toOffsetDateTime(), OffsetDateTime::class.java)
            .bindNullable("failedAt", notification.failedAt?.toOffsetDateTime(), OffsetDateTime::class.java)
            .bindNullable("errorMessage", notification.errorMessage, String::class.java)
            .bind("updatedAt", notification.updatedAt.toOffsetDateTime())
            .bind("id", notification.id.value)
            .fetch()
            .rowsUpdated()
            .asFlow()
            .let { it.firstOrNull() }
        return notification
    }

    override suspend fun findByIdempotencyKey(key: IdempotencyKey): Notification? =
        databaseClient.sql(SELECT_BY_KEY_SQL)
            .bind("idempotencyKey", key.value)
            .map { row, _ -> row.toNotification() }
            .one()
            .asFlow()
            .firstOrNull()

    companion object {
        private val JSONB_PAIR: Regex = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")

        private const val INSERT_SQL = """
            INSERT INTO notifications
              (id, idempotency_key, channel, recipient, template_id, payload,
               status, sent_at, failed_at, error_message, created_at, updated_at)
            VALUES
              (:id, :idempotencyKey, :channel, :recipient, :templateId, :payload::jsonb,
               :status, :sentAt, :failedAt, :errorMessage, :createdAt, :updatedAt)
        """

        private const val UPDATE_SQL = """
            UPDATE notifications SET
              status = :status,
              sent_at = :sentAt,
              failed_at = :failedAt,
              error_message = :errorMessage,
              updated_at = :updatedAt
            WHERE id = :id
        """

        private const val SELECT_BY_KEY_SQL = """
            SELECT id, idempotency_key, channel, recipient, template_id, payload,
                   status, sent_at, failed_at, error_message, created_at, updated_at
            FROM notifications
            WHERE idempotency_key = :idempotencyKey
        """

        private fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

        private fun Map<String, String>.toJsonb(): String = buildString {
            append('{')
            entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"')
            }
            append('}')
        }

        private fun escape(value: String): String = buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    else -> append(character)
                }
            }
        }

        private fun io.r2dbc.spi.Readable.toNotification(): Notification {
            val id = get("id", String::class.java)!!
            val idempotencyKey = get("idempotency_key", String::class.java)!!
            val channel = get("channel", String::class.java)!!
            val recipient = get("recipient", String::class.java)!!
            val templateId = get("template_id", String::class.java)!!
            val payload = get("payload", String::class.java)!!
            val status = get("status", String::class.java)!!
            val sentAt = get("sent_at", OffsetDateTime::class.java)?.toInstant()
            val failedAt = get("failed_at", OffsetDateTime::class.java)?.toInstant()
            val errorMessage = get("error_message", String::class.java)
            val createdAt = get("created_at", OffsetDateTime::class.java)!!.toInstant()
            val updatedAt = get("updated_at", OffsetDateTime::class.java)!!.toInstant()

            return Notification(
                id = NotificationId(id),
                idempotencyKey = IdempotencyKey(idempotencyKey),
                channel = NotificationChannel.valueOf(channel),
                recipient = Recipient(recipient),
                templateId = TemplateId(templateId),
                payload = NotificationPayload(payload.fromJsonb()),
                status = NotificationStatus.valueOf(status),
                sentAt = sentAt,
                failedAt = failedAt,
                errorMessage = errorMessage,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun String.fromJsonb(): Map<String, String> {
            val trimmed = trim()
            if (trimmed.isEmpty() || trimmed == "{}") return emptyMap()
            val result = mutableMapOf<String, String>()
            JSONB_PAIR.findAll(trimmed).forEach { match ->
                val (rawKey, rawValue) = match.destructured
                result[unescapeJsonb(rawKey)] = unescapeJsonb(rawValue)
            }
            return result
        }

        private fun unescapeJsonb(value: String): String = value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

/**
 * Thrown when a notification insert collides with an existing record (typically the
 * unique idempotency_key constraint). The consumer treats this as a benign signal that
 * the same logical notification was attempted twice.
 */
class DuplicateNotificationException(val idempotencyKey: IdempotencyKey, cause: Throwable) :
    RuntimeException("Notification with idempotency key '${idempotencyKey.value}' already exists", cause)

private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value == null) {
    bindNull(name, type)
} else {
    bind(name, value)
}
