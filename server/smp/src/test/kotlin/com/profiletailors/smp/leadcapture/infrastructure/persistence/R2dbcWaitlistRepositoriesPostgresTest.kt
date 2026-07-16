package com.profiletailors.smp.leadcapture.infrastructure.persistence

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcWaitlistRepositoriesPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val waitlistRepository by lazy { R2dbcWaitlistRepository(databaseClient) }
    private val entryRepository by lazy { R2dbcWaitlistEntryRepository(databaseClient) }

    @BeforeEach
    fun cleanLeadCaptureTables() {
        runBlocking {
            databaseClient.sql("DELETE FROM waitlist_entries").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql(
                "DELETE FROM waitlists WHERE id <> 'profile-tailors-launch'",
            ).fetch().rowsUpdated().awaitSingle()
        }
    }

    @Test
    fun `findByKey maps the seeded profile tailors launch waitlist`() = runTest {
        val waitlist = waitlistRepository.findByKey(
            com.profiletailors.leadcapture.waitlist.domain.WaitlistKey("profile-tailors-launch"),
        )

        assertNotNull(waitlist)
        assertEquals("profile-tailors-launch", waitlist?.id?.value)
        assertEquals("profile-tailors-launch", waitlist?.key?.value)
        assertEquals("Profile Tailors Launch", waitlist?.name)
        assertEquals("profile-tailors", waitlist?.context)
        assertEquals(com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus.ACTIVE, waitlist?.status)
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
    fun `saveIfNotExists returns AlreadyExists for duplicate email in same waitlist`() = runTest {
        val first = testEntry(id = "entry-1", email = "User@example.com")
        val duplicate = testEntry(id = "entry-2", email = "user@example.com")

        val firstResult = entryRepository.saveIfNotExists(first)
        val duplicateResult = entryRepository.saveIfNotExists(duplicate)

        kotlin.test.assertIs<WaitlistEntryRepository.SaveResult.Saved>(firstResult)
        val alreadyExists = kotlin.test.assertIs<WaitlistEntryRepository.SaveResult.AlreadyExists>(duplicateResult)
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

        val firstResult = entryRepository.saveIfNotExists(first)
        val secondResult = entryRepository.saveIfNotExists(second)

        kotlin.test.assertIs<WaitlistEntryRepository.SaveResult.Saved>(firstResult)
        kotlin.test.assertIs<WaitlistEntryRepository.SaveResult.Saved>(secondResult)
        assertEquals(1, countEntries(first.waitlistId))
        assertEquals(1, countEntries(otherWaitlistId))
    }

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
            consent = com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent(
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
