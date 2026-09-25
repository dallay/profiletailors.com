package com.profiletailors.smp.leadcapture.infrastructure.persistence

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalTokenIssuer
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.WelcomeEmail
import com.profiletailors.notifications.domain.WelcomeEmailTemplateId
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.leadcapture.infrastructure.notification.EncryptedWaitlistWithdrawalUrlProvider
import com.profiletailors.smp.leadcapture.infrastructure.notification.R2dbcWaitlistWithdrawalUrlRepository
import com.profiletailors.smp.leadcapture.infrastructure.notification.WaitlistWithdrawalProperties
import com.profiletailors.smp.notifications.infrastructure.persistence.NotificationsSchemaInitializer
import com.profiletailors.smp.notifications.infrastructure.persistence.R2dbcNotificationRepository
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
import java.util.Base64
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcWaitlistRepositoriesPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val waitlistRepository by lazy { R2dbcWaitlistRepository(databaseClient) }
    private val withdrawalUrlRepository by lazy { R2dbcWaitlistWithdrawalUrlRepository(databaseClient) }
    private val withdrawalUrlProvider by lazy {
        EncryptedWaitlistWithdrawalUrlProvider(
            withdrawalUrlRepository,
            WaitlistWithdrawalProperties(
                encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
                publicUrlBase = "https://profiletailors.com/waitlist/withdraw",
            ),
        )
    }
    private val entryRepository by lazy {
        R2dbcWaitlistEntryRepository(databaseClient, withdrawalUrlProvider = withdrawalUrlProvider)
    }
    private val notificationRepository by lazy { R2dbcNotificationRepository(databaseClient) }

    @BeforeEach
    fun cleanLeadCaptureTables() {
        NotificationsSchemaInitializer(databaseClient).initializeSchema()
        runBlocking {
            databaseClient.sql("DELETE FROM notifications WHERE template_id = 'waitlist.welcome'")
                .fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("DELETE FROM waitlist_withdrawal_urls").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("DELETE FROM waitlist_entries").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql(
                "DELETE FROM waitlists WHERE id <> 'profile-tailors-launch'",
            ).fetch().rowsUpdated().awaitSingle()
        }
    }

    @Test
    fun `withdrawal url repository stores and deletes encrypted ciphertext`() = runTest {
        val repository = R2dbcWaitlistWithdrawalUrlRepository(databaseClient)
        val entry = testEntry(id = "entry-withdrawal-url", email = "withdrawal@example.com")
        entryRepository.save(entry)
        val expiresAt = Instant.parse("2026-10-01T00:00:00Z")

        repository.save(entry.id, "v1", "ciphertext", expiresAt)

        val stored = repository.findByEntryId(entry.id)
        assertNotNull(stored)
        assertEquals("v1", stored?.ciphertextVersion)
        assertEquals("ciphertext", stored?.ciphertext)
        assertEquals(expiresAt, stored?.expiresAt)

        val restartedRepository = R2dbcWaitlistWithdrawalUrlRepository(databaseClient)
        val restartedStored = restartedRepository.findByEntryId(entry.id)
        assertNotNull(restartedStored)
        assertEquals("v1", restartedStored?.ciphertextVersion)
        assertEquals("ciphertext", restartedStored?.ciphertext)
        assertEquals(expiresAt, restartedStored?.expiresAt)

        repository.deleteByEntryId(entry.id)

        assertNull(restartedRepository.findByEntryId(entry.id))
    }

    @Test
    fun `findByKey maps the seeded profile tailors launch waitlist`() = runTest {
        val waitlist = waitlistRepository.findByKey(
            WaitlistKey("profile-tailors-launch"),
        )

        assertNotNull(waitlist)
        assertEquals("profile-tailors-launch", waitlist?.id?.value)
        assertEquals("profile-tailors-launch", waitlist?.key?.value)
        assertEquals("Profile Tailors Launch", waitlist?.name)
        assertEquals("profile-tailors", waitlist?.context)
        assertEquals(WaitlistStatus.ACTIVE, waitlist?.status)
    }

    @Test
    fun `save and findByNormalizedEmail round-trip a waitlist entry`() = runTest {
        val entry = testEntry(id = "entry-1", email = "User@example.com")

        entryRepository.save(entry)

        val persisted = entryRepository.findByNormalizedEmail(entry.waitlistId, entry.normalizedEmail)
        assertNotNull(persisted)
        assertEquals(entry.id, persisted?.id)
        assertEquals(entry.email.toString(), persisted?.email?.toString())
        assertEquals(entry.normalizedEmail.value, persisted?.normalizedEmail?.value)
        assertEquals(entry.source.value, persisted?.source?.value)
        assertEquals(entry.formId, persisted?.formId)
        assertEquals(entry.locale?.value, persisted?.locale?.value)
        assertEquals(entry.metadata.pagePath, persisted?.metadata?.pagePath)
        assertEquals(entry.consent, persisted?.consent)
        assertEquals(entry.status, persisted?.status)
    }

    @Test
    fun `save and findByNormalizedEmail round-trip lifecycle timestamps`() = runTest {
        val entry = testEntry(id = "entry-lifecycle", email = "lifecycle@example.com")
        val invitedAt = Instant.parse("2026-07-17T00:00:00Z")
        val convertedAt = Instant.parse("2026-07-18T00:00:00Z")
        entry.invite(invitedAt)
        entry.convert(convertedAt)

        entryRepository.save(entry)

        val persisted = entryRepository.findByNormalizedEmail(entry.waitlistId, entry.normalizedEmail)
        assertNotNull(persisted)
        assertEquals(WaitlistEntryStatus.CONVERTED, persisted?.status)
        assertEquals(invitedAt, persisted?.invitedAt)
        assertEquals(convertedAt, persisted?.convertedAt)
    }

    @Test
    fun `save and findByNormalizedEmail round-trip cancelled timestamp`() = runTest {
        val entry = testEntry(id = "entry-cancelled", email = "cancelled@example.com")
        val cancelledAt = Instant.parse("2026-07-17T00:00:00Z")
        entry.cancel(cancelledAt)

        entryRepository.save(entry)

        val persisted = entryRepository.findByNormalizedEmail(entry.waitlistId, entry.normalizedEmail)
        assertNotNull(persisted)
        assertEquals(WaitlistEntryStatus.CANCELLED, persisted?.status)
        assertEquals(cancelledAt, persisted?.cancelledAt)
    }

    @Test
    fun `saveIfNotExists returns AlreadyExists for duplicate email in same waitlist`() = runTest {
        val first = testEntry(id = "entry-1", email = "User@example.com")
        val duplicate = testEntry(id = "entry-2", email = "user@example.com")

        val firstResult = entryRepository.saveIfNotExists(first, tokenFor(first.id.value))
        val duplicateResult = entryRepository.saveIfNotExists(duplicate, tokenFor(duplicate.id.value))

        assertIs<WaitlistEntryRepository.SaveResult.Saved>(firstResult)
        val alreadyExists = assertIs<WaitlistEntryRepository.SaveResult.AlreadyExists>(duplicateResult)
        assertEquals(first.id, alreadyExists.existing.id)
        assertEquals(1, countEntries(first.waitlistId))
    }

    @Test
    fun `dedupe key is scoped per waitlist`() = runTest {
        val first =
            testEntry(id = "entry-1", waitlistId = WaitlistId("profile-tailors-launch"), email = "user@example.com")
        val otherWaitlistId = WaitlistId("another-launch")
        insertWaitlist(otherWaitlistId.value, "another-launch")
        val second = testEntry(id = "entry-2", waitlistId = otherWaitlistId, email = "USER@example.com")

        val firstResult = entryRepository.saveIfNotExists(first, tokenFor(first.id.value))
        val secondResult = entryRepository.saveIfNotExists(second, tokenFor(second.id.value))

        assertIs<WaitlistEntryRepository.SaveResult.Saved>(firstResult)
        assertIs<WaitlistEntryRepository.SaveResult.Saved>(secondResult)
        assertEquals(1, countEntries(first.waitlistId))
        assertEquals(1, countEntries(otherWaitlistId))
    }

    @Test
    fun `withdrawal anonymizes the entry and welcome notification and allows rejoin`() = runTest {
        val now = Instant.parse("2026-07-20T10:00:00Z")
        val entry = testEntry(id = "entry-withdraw", email = "returning@example.com")
        val issued = WaitlistWithdrawalTokenIssuer.secure.issue(now)
        assertIs<WaitlistEntryRepository.SaveResult.Saved>(entryRepository.saveIfNotExists(entry, issued.persisted))
        withdrawalUrlProvider.remember(entry.id, issued.raw, issued.persisted.expiresAt)
        notificationRepository.save(
            Notification(
                id = NotificationId("welcome-entry-withdraw"),
                idempotencyKey = WelcomeEmail.idempotencyKeyFor(entry.id),
                channel = NotificationChannel.EMAIL,
                recipient = Recipient(entry.normalizedEmail.value),
                templateId = WelcomeEmailTemplateId.INSTANCE,
                payload = WelcomeEmail.payloadFor(entry.id, "Profile Tailors Launch", "en"),
                status = NotificationStatus.PENDING,
                sentAt = null,
                failedAt = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val fingerprint = WaitlistWithdrawalTokenIssuer.fingerprint(issued.raw)
        val withdrawn = requireNotNull(entryRepository.withdrawByToken(fingerprint.candidate, fingerprint.hash, now))
        assertEquals(WaitlistEntryStatus.CANCELLED, withdrawn.status)
        assertEquals("withdrawn-${entry.id.value}@invalid.example", withdrawn.email.value)
        assertEquals("withdrawn-${entry.id.value}", withdrawn.normalizedEmail.value)
        assertFalse(withdrawn.consent.earlyAccess)
        assertFalse(withdrawn.consent.marketing)
        assertEquals(now, withdrawn.cancelledAt)
        val persisted = entryRepository.findByNormalizedEmail(entry.waitlistId, withdrawn.normalizedEmail)
        assertEquals(withdrawn.id, persisted?.id)
        assertEquals(withdrawn.status, persisted?.status)
        assertEquals(withdrawn.email, persisted?.email)
        assertNull(withdrawalUrlRepository.findByEntryId(entry.id))
        val notification = notificationRepository.findById(NotificationId("welcome-entry-withdraw"))
        assertEquals("withdrawn-${entry.id.value}@invalid.example", notification?.recipient?.value)
        assertEquals(NotificationStatus.FAILED, notification?.status)
        assertNull(entryRepository.withdrawByToken(fingerprint.candidate, fingerprint.hash, now))

        val returning = testEntry(id = "entry-rejoined", email = "returning@example.com")
        val rejoinToken = WaitlistWithdrawalTokenIssuer.secure.issue(now)
        assertIs<WaitlistEntryRepository.SaveResult.Saved>(
            entryRepository.saveIfNotExists(returning, rejoinToken.persisted),
        )
        assertEquals(2, countEntries(entry.waitlistId))
        assertTrue(returning.id != withdrawn.id)
    }

    @Test
    fun `expired converted and wrong-hash tokens cannot withdraw entries`() = runTest {
        val now = Instant.parse("2026-07-20T10:00:00Z")
        val expiredEntry = testEntry(id = "entry-expired", email = "expired@example.com")
        val expiredToken = WaitlistWithdrawalTokenIssuer.secure.issue(now)
        entryRepository.saveIfNotExists(expiredEntry, expiredToken.persisted)
        val expiredFingerprint = WaitlistWithdrawalTokenIssuer.fingerprint(expiredToken.raw)
        assertNull(
            entryRepository.withdrawByToken(
                expiredFingerprint.candidate,
                expiredFingerprint.hash,
                expiredToken.persisted.expiresAt,
            ),
        )

        val convertedEntry = testEntry(id = "entry-converted", email = "converted@example.com")
        convertedEntry.invite(now)
        convertedEntry.convert(now.plusSeconds(1))
        val convertedToken = WaitlistWithdrawalTokenIssuer.secure.issue(now)
        entryRepository.saveIfNotExists(convertedEntry, convertedToken.persisted)
        val convertedFingerprint = WaitlistWithdrawalTokenIssuer.fingerprint(convertedToken.raw)
        assertNull(entryRepository.withdrawByToken(convertedFingerprint.candidate, convertedFingerprint.hash, now))

        val wrongHashEntry = testEntry(id = "entry-wrong-hash", email = "wrong-hash@example.com")
        val wrongHashToken = WaitlistWithdrawalTokenIssuer.secure.issue(now)
        entryRepository.saveIfNotExists(wrongHashEntry, wrongHashToken.persisted)
        val wrongHashFingerprint = WaitlistWithdrawalTokenIssuer.fingerprint(wrongHashToken.raw)
        assertNull(entryRepository.withdrawByToken(wrongHashFingerprint.candidate, "0".repeat(64), now))
        assertEquals(
            WaitlistEntryStatus.PENDING,
            entryRepository.findByNormalizedEmail(
                wrongHashEntry.waitlistId,
                wrongHashEntry.normalizedEmail,
            )?.status,
        )
    }

    private fun tokenFor(value: String): WaitlistEntryRepository.WithdrawalToken =
        WaitlistEntryRepository.WithdrawalToken(
            candidate = "candidate-$value",
            hash = "hash-$value",
            expiresAt = Instant.parse("2026-10-16T00:00:00Z"),
        )

    private fun testEntry(
        id: String,
        waitlistId: WaitlistId = WaitlistId("profile-tailors-launch"),
        email: String,
    ): WaitlistEntry {
        val emailAddress = EmailAddress(email)
        return WaitlistEntry(
            id = WaitlistEntryId(id),
            waitlistId = waitlistId,
            email = emailAddress,
            normalizedEmail = NormalizedEmail.from(emailAddress),
            source = CaptureSource("marketing-homepage"),
            formId = "launch-form",
            locale = CaptureLocale("en"),
            metadata = LeadMetadata(pagePath = "/", consentVersion = "2026-07-16"),
            consent = WaitlistConsent(
                earlyAccess = true,
                marketing = false,
                version = "2026-07-16",
            ),
            joinedAt = Instant.parse("2026-07-16T00:00:00Z"),
        )
    }

    private suspend fun insertWaitlist(id: String, key: String) {
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES (:id, :key, 'Another Launch', 'profile-tailors', 'ACTIVE')
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("key", key)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun countEntries(waitlistId: WaitlistId): Int = databaseClient.sql(
        "SELECT COUNT(*) AS cnt FROM waitlist_entries WHERE waitlist_id = :waitlistId",
    )
        .bind("waitlistId", waitlistId.value)
        .map { row, _ -> (row.get("cnt") as Number).toInt() }
        .one()
        .awaitSingle()

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("lead_capture_waitlist_repository")
    }
}
