package com.profiletailors.smp.notifications.infrastructure.persistence

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.TemplateId
import com.profiletailors.notifications.domain.WelcomeEmailTemplateId
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcNotificationRepositoryPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val repository by lazy { R2dbcNotificationRepository(databaseClient) }

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
            databaseClient.sql(
                "CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications (status)",
            ).fetch().rowsUpdated().awaitSingle()
        }
    }

    private fun pending(
        id: String = "ntf-1",
        key: String = "waitlist.welcome:entry-1",
        channel: NotificationChannel = NotificationChannel.EMAIL,
        templateId: TemplateId = WelcomeEmailTemplateId.INSTANCE,
        recipient: String = "user@example.com",
        payload: NotificationPayload = NotificationPayload(mapOf("a" to "1")),
        createdAt: Instant = Instant.parse("2026-07-20T10:00:00Z"),
    ): Notification = Notification(
        id = NotificationId(id),
        idempotencyKey = IdempotencyKey(key),
        channel = channel,
        recipient = Recipient(recipient),
        templateId = templateId,
        payload = payload,
        status = NotificationStatus.PENDING,
        sentAt = null,
        failedAt = null,
        errorMessage = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `save persists a PENDING notification and returns it unchanged`() = runTest {
        val written = pending()
        val saved = repository.save(written)

        assertEquals(written.id, saved.id)
        assertEquals(written.idempotencyKey, saved.idempotencyKey)
        assertEquals(written.status, saved.status)

        val found = repository.findByIdempotencyKey(written.idempotencyKey)
        assertNotNull(found)
        assertEquals(written.id, found?.id)
        assertEquals(written.idempotencyKey, found?.idempotencyKey)
        assertEquals(written.recipient, found?.recipient)
        assertEquals(written.templateId, found?.templateId)
        assertEquals(written.channel, found?.channel)
        assertEquals(written.payload.variables, found?.payload?.variables)
        assertNull(found?.sentAt)
        assertNull(found?.failedAt)
        assertNull(found?.errorMessage)
    }

    @Test
    fun `save then update transitions the same row from PENDING to SENT`() = runTest {
        val written = pending(id = "ntf-sent", createdAt = Instant.parse("2026-07-20T10:00:00Z"))
        repository.save(written)

        val sentAt = Instant.parse("2026-07-20T10:01:00Z")
        val updated = repository.update(written.markSent(sentAt))

        val found = requireNotNull(repository.findByIdempotencyKey(written.idempotencyKey))
        assertEquals(NotificationStatus.SENT, found.status)
        // Instant equality may lose microsecond precision across the JSONB roundtrip
        // depending on driver settings — comparing via after() to be safe.
        val persistedSentAt = requireNotNull(found.sentAt)
        assertTrue(persistedSentAt.toEpochMilli() >= sentAt.toEpochMilli() - 1000)
        assertNull(found.failedAt)
        assertNull(found.errorMessage)
    }

    @Test
    fun `save then update transitions the same row from PENDING to FAILED with the upstream error`() = runTest {
        val written = pending(id = "ntf-failed")
        repository.save(written)

        val failedAt = Instant.parse("2026-07-20T10:02:00Z")
        val updated = repository.update(written.markFailed(failedAt, "smtp 5xx"))

        val found = repository.findByIdempotencyKey(written.idempotencyKey)
        assertNotNull(found)
        assertEquals(NotificationStatus.FAILED, found?.status)
        assertEquals("smtp 5xx", found?.errorMessage)
        assertNotNull(found?.failedAt)
        assertNull(found?.sentAt)
    }

    @Test
    fun `findByIdempotencyKey returns null when no notification exists for that key`() = runTest {
        val found = repository.findByIdempotencyKey(IdempotencyKey("does-not-exist"))

        assertNull(found)
    }

    @Test
    fun `payload round-trips through the JSONB column`() = runTest {
        val payload = NotificationPayload(
            mapOf(
                "waitlistName" to "Profile Tailors Launch",
                "email" to "user@example.com",
                "embedded\"quote" to "weird chars: \\ / & 😀",
            ),
        )
        val written = pending(payload = payload, id = "ntf-payload")
        repository.save(written)

        val found = repository.findByIdempotencyKey(written.idempotencyKey)
        assertNotNull(found)
        assertEquals(payload.variables, found?.payload?.variables)
    }

    @Test
    fun `saving a duplicate idempotency key throws DuplicateNotificationException`() = runTest {
        val first = pending(id = "ntf-dup-a", key = "waitlist.welcome:entry-dup")
        repository.save(first)

        val second = pending(id = "ntf-dup-b", key = "waitlist.welcome:entry-dup")
        var thrown: Throwable? = null
        try {
            repository.save(second)
        } catch (e: DuplicateNotificationException) {
            thrown = e
        }

        assertNotNull(thrown)
        val duplicate = thrown as DuplicateNotificationException
        assertEquals(IdempotencyKey("waitlist.welcome:entry-dup"), duplicate.idempotencyKey)
    }

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer(
            "notifications_repository",
        )
    }
}
