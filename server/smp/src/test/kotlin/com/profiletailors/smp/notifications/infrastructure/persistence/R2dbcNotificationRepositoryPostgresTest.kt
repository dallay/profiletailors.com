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
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcNotificationRepositoryPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val repository by lazy { R2dbcNotificationRepository(databaseClient) }

    private fun notificationId(seed: String): String =
        UUID.nameUUIDFromBytes("notifications:$seed".toByteArray()).toString()

    @BeforeEach
    fun prepareNotificationsTable() {
        runBlocking {
            NotificationsSchemaInitializer(databaseClient).initializeSchema()
            databaseClient.sql("DELETE FROM notifications").fetch().rowsUpdated().awaitSingle()
        }
    }

    private fun pending(
        id: String = notificationId("pending"),
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
    fun `initializer creates native UUID identifiers for a new installation`() = runTest {
        NotificationsSchemaInitializer(databaseClient).initializeSchema()
        val type = databaseClient.sql(
            "SELECT data_type FROM information_schema.columns WHERE table_schema = current_schema() " +
                "AND table_name = 'notifications' AND column_name = 'id'",
        ).map { row, _ -> requireNotNull(row.get("data_type", String::class.java)) }.one().awaitSingle()

        assertEquals("uuid", type)
    }

    @Test
    fun `native UUID identifiers round-trip through lookup claim and update`() = runTest {
        val written = pending(id = notificationId("native"))
        repository.save(written)

        val storedId = databaseClient.sql("SELECT id FROM notifications WHERE idempotency_key = :key")
            .bind("key", written.idempotencyKey.value)
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)) }.one().awaitSingle()
        assertEquals(UUID.fromString(written.id.value), storedId)
        assertEquals(written.id, repository.findById(written.id)?.id)
        assertNull(repository.findById(NotificationId(notificationId("missing"))))

        val now = written.createdAt.plusSeconds(60)
        val claimed = repository.claimPending(written.templateId, now, written.createdAt.minusSeconds(60), 1)
        assertEquals(listOf(written.id), claimed.map { it.id })
        assertEquals(NotificationStatus.DISPATCHING, claimed.single().status)
        repository.update(claimed.single().markSent(now))
        assertEquals(NotificationStatus.SENT, repository.findById(written.id)?.status)
    }

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
        val written = pending(id = notificationId("sent"), createdAt = Instant.parse("2026-07-20T10:00:00Z"))
        repository.save(written)

        val sentAt = Instant.parse("2026-07-20T10:01:00Z")
        val updated = repository.update(written.markSent(sentAt))

        val found = requireNotNull(repository.findByIdempotencyKey(written.idempotencyKey))
        assertEquals(NotificationStatus.SENT, found.status)
        val persistedSentAt = requireNotNull(found.sentAt)
        assertTrue(abs(persistedSentAt.toEpochMilli() - sentAt.toEpochMilli()) <= 1000)
        assertNull(found.failedAt)
        assertNull(found.errorMessage)
    }

    @Test
    fun `save then update transitions the same row from PENDING to FAILED with the upstream error`() = runTest {
        val written = pending(id = notificationId("failed"))
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
        val written = pending(payload = payload, id = notificationId("payload"))
        repository.save(written)

        val found = repository.findByIdempotencyKey(written.idempotencyKey)
        assertNotNull(found)
        assertEquals(payload.variables, found?.payload?.variables)
    }

    @Test
    fun `saving a duplicate idempotency key throws DuplicateNotificationException`() = runTest {
        val first = pending(id = notificationId("dup-a"), key = "waitlist.welcome:entry-dup")
        repository.save(first)

        val second = pending(id = notificationId("dup-b"), key = "waitlist.welcome:entry-dup")
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

    @Test
    fun `invitation initial and resend keys persist as distinct rows`() = runTest {
        val invitationId = UUID.randomUUID().toString()
        val initial = pending(id = notificationId("inv-initial"), key = "invitation:$invitationId:initial")
        repository.save(initial)

        val deliveryId = UUID.randomUUID().toString()
        val resend = pending(id = notificationId("inv-resend"), key = "invitation:$invitationId:resend:$deliveryId")
        repository.save(resend)

        val foundInitial = requireNotNull(repository.findByIdempotencyKey(initial.idempotencyKey))
        val foundResend = requireNotNull(repository.findByIdempotencyKey(resend.idempotencyKey))
        assertEquals(initial.id, foundInitial.id)
        assertEquals(resend.id, foundResend.id)
        assertTrue(foundInitial.idempotencyKey != foundResend.idempotencyKey)
    }

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer(
            "notifications_repository",
        )
    }
}
