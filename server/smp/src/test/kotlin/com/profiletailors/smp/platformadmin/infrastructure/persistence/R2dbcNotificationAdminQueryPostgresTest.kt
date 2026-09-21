package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.notifications.infrastructure.persistence.R2dbcNotificationRepository
import com.profiletailors.smp.platformadmin.application.query.NotificationFilters
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcNotificationAdminQueryPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val writer by lazy { R2dbcNotificationRepository(databaseClient) }
    private val query by lazy { R2dbcNotificationAdminQueryAdapter(databaseClient) }

    @BeforeEach
    fun prepareNotificationsTable() {
        runBlocking {
            ensureSchemaExists()
            databaseClient.sql("DELETE FROM notifications").fetch().rowsUpdated().awaitSingle()
        }
    }

    private fun ensureSchemaExists() {
        runBlocking {
            databaseClient.sql(
                """
                CREATE TABLE IF NOT EXISTS notifications (
                    id VARCHAR(255) PRIMARY KEY,
                    idempotency_key VARCHAR(500) UNIQUE NOT NULL,
                    channel VARCHAR(50) NOT NULL,
                    recipient VARCHAR(500) NOT NULL,
                    template_id VARCHAR(255) NOT NULL,
                    payload JSONB NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    sent_at TIMESTAMP,
                    failed_at TIMESTAMP,
                    error_message TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
        }
    }

    private fun failedNotification(
        id: String,
        templateId: String = "platform.password-recovery",
        channel: NotificationChannel = NotificationChannel.EMAIL,
        recipient: String = "user@example.com",
    ): Notification {
        val now = Instant.parse("2026-09-20T10:00:00Z")
        return Notification(
            id = NotificationId(id),
            idempotencyKey = IdempotencyKey("key-$id"),
            channel = channel,
            recipient = Recipient(recipient),
            templateId = TemplateId(templateId),
            payload = NotificationPayload(mapOf("message" to "Reset your password", "token" to "secret-abc")),
            status = NotificationStatus.FAILED,
            sentAt = null,
            failedAt = now,
            errorMessage = "smtp timeout",
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `list returns empty page when no notifications exist`() = runTest {
        val result = query.list(NotificationFilters(), page = 0, size = 20)

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totalElements)
    }

    @Test
    fun `list returns seeded notifications with mapped fields`() = runTest {
        writer.save(failedNotification("ntf-1"))

        val result = query.list(NotificationFilters(), page = 0, size = 20)

        assertEquals(1, result.totalElements)
        assertEquals(1, result.items.size)
        val summary = result.items.first()
        assertEquals("ntf-1", summary.id)
        assertEquals("EMAIL", summary.channel)
        assertEquals("platform.password-recovery", summary.templateId)
        assertEquals("user@example.com", summary.recipient)
        assertEquals("FAILED", summary.status)
        assertEquals("smtp timeout", summary.errorMessage)
        assertNotNull(summary.createdAt)
        assertNotNull(summary.failedAt)
        assertEquals("[REDACTED]", summary.redactedPayload["token"])
        assertEquals("Reset your password", summary.redactedPayload["message"])
    }

    @Test
    fun `list filters by status`() = runTest {
        val now = Instant.parse("2026-09-20T10:00:00Z")
        writer.save(failedNotification("ntf-1"))
        writer.save(
            failedNotification("ntf-2").copy(
                status = NotificationStatus.SENT,
                sentAt = now,
                failedAt = null,
                errorMessage = null,
            ),
        )

        val result = query.list(NotificationFilters(status = "FAILED"), page = 0, size = 20)

        assertEquals(1, result.totalElements)
        assertEquals("ntf-1", result.items.first().id)
    }

    @Test
    fun `findById returns summary when notification exists`() = runTest {
        writer.save(failedNotification("ntf-1"))

        val result = query.findById(NotificationId("ntf-1"))

        assertNotNull(result)
        assertEquals("ntf-1", result.id)
    }

    @Test
    fun `findById returns null when notification is missing`() = runTest {
        val result = query.findById(NotificationId("ntf-missing"))

        assertNull(result)
    }

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer(
            "notifications_admin_query",
        )
    }
}
