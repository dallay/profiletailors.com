package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import com.profiletailors.smp.governance.domain.SubjectReference
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcConsentRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val repository by lazy { R2dbcConsentRepository(databaseClient, transactionalOperator) }
    private val workspaceScope = ConsentScope.Workspace(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @BeforeEach
    fun seedConsentWorkspaces() = runTest {
        listOf(
            workspaceScope.id,
            UUID.fromString("00000000-0000-0000-0000-000000000021"),
            UUID.fromString("00000000-0000-0000-0000-000000000022"),
        ).forEach { id ->
            databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES (:id, 'Consent Workspace', 'ACTIVE')")
                .bind("id", id).fetch().rowsUpdated().awaitSingle()
        }
    }

    @Test
    fun `native UUID consent preserves anonymous subjects through recording and withdrawal`() = runTest {
        val scope = workspaceScope
        val record = consentRecord(
            id = "00000000-0000-0000-0000-000000000002",
            scope = scope,
            subjectReference = SubjectReference.anonymous("opaque-anonymous-subject"),
        )

        val (inserted, saved) = repository.recordActiveReturning(record)

        assertTrue(inserted)
        assertEquals(record.id, saved.id)
        assertEquals(record.subjectReference, repository.findById(scope, record.id)?.subjectReference)
        assertEquals(1, repository.findActiveByScope(scope, null, null).toList().size)

        val withdrawn = repository.withdrawActiveReturning(
            scope,
            record.subjectReference,
            record.purpose,
            record.policyVersion,
            Instant.parse("2026-08-01T10:00:00Z"),
            "withdrawn by subject",
        )

        assertEquals(ConsentStatus.WITHDRAWN, withdrawn?.status)
        assertFalse(repository.existsActive(scope, record.subjectReference, record.purpose, record.policyVersion))
        assertEquals(
            1,
            repository.findHistoricalByIdentity(scope, record.subjectReference, record.purpose).toList().size,
        )
    }

    @Test
    fun `saves and finds consent by id`() = runTest {
        val consent = consentRecord(id = "00000000-0000-0000-0000-000000000010")

        repository.recordActiveReturning(consent)

        val eventId = databaseClient.sql("SELECT id FROM consent_record_events LIMIT 1")
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)).toString() }
            .first()
            .awaitSingle()
        assertEquals(eventId, java.util.UUID.fromString(eventId).toString())

        val found = repository.findById(consent.scope, consent.id)
        assertNotNull(found)
        assertEquals(consent.id, found.id)
        assertEquals(consent.scope, found.scope)
        assertEquals(consent.subjectReference, found.subjectReference)
        assertEquals(consent.consentType, found.consentType)
        assertEquals(consent.purpose, found.purpose)
        assertEquals(consent.policyVersion, found.policyVersion)
        assertEquals(consent.status, found.status)
        assertEquals(consent.givenAt, found.givenAt)
    }

    @Test
    fun `findActive is scoped by workspace to prevent cross workspace disclosure`() = runTest {
        val subject = SubjectReference.user("00000000-0000-0000-0000-000000000020")
        val workspace1 = ConsentScope.Workspace(UUID.fromString("00000000-0000-0000-0000-000000000021"))
        val workspace2 = ConsentScope.Workspace(UUID.fromString("00000000-0000-0000-0000-000000000022"))
        val record1 = consentRecord(scope = workspace1, subjectReference = subject)
        val record2 = consentRecord(scope = workspace2, subjectReference = subject)
        repository.save(record1)
        repository.save(record2)

        val ws1 = repository.findActive(workspace1, subject, "marketing_emails", "2026-07-01")
        val ws2 = repository.findActive(workspace2, subject, "marketing_emails", "2026-07-01")

        assertEquals(record1.id, ws1?.id)
        assertEquals(record2.id, ws2?.id)
    }

    @Test
    fun `findActive ignores withdrawn records`() = runTest {
        val subject = SubjectReference.workspace("00000000-0000-0000-0000-000000000001")
        val withdrawn = consentRecord(subjectReference = subject)
            .withdraw(at = Instant.parse("2026-08-01T10:00:00Z"))
        repository.save(withdrawn)

        val result = repository.findActive(withdrawn.scope, subject, "marketing_emails", "2026-07-01")

        assertNull(result)
    }

    @Test
    fun `existsActive returns true only for active workspace scoped record`() = runTest {
        val subject = SubjectReference.user("00000000-0000-0000-0000-000000000020")
        val record = consentRecord(subjectReference = subject)
        repository.save(record)

        assertTrue(repository.existsActive(record.scope, subject, "marketing_emails", "2026-07-01"))
        assertFalse(
            repository.existsActive(
                ConsentScope.Workspace(UUID.fromString("00000000-0000-0000-0000-000000000022")),
                subject,
                "marketing_emails",
                "2026-07-01",
            ),
        )
    }

    @Test
    fun `findActiveByScope filters by subject kind and purpose`() = runTest {
        val userConsent = consentRecord(
            subjectReference = SubjectReference.user("00000000-0000-0000-0000-000000000020"),
        )
        repository.save(userConsent)
        repository.save(consentRecord(subjectReference = SubjectReference.anonymous("lead-hash")))
        repository.save(
            consentRecord(
                subjectReference = SubjectReference.user("00000000-0000-0000-0000-000000000023"),
                purpose = "terms.v1",
            ),
        )

        val marketingUsers = repository.findActiveByScope(
            scope = userConsent.scope,
            subjectKind = SubjectKind.USER,
            purpose = "marketing_emails",
        ).toList()

        assertEquals(listOf(userConsent.id), marketingUsers.map { it.id })
    }

    @Test
    fun `findHistoricalByIdentity returns active and withdrawn history ordered by givenAt`() = runTest {
        val subject = SubjectReference.user("00000000-0000-0000-0000-000000000020")
        repository.save(
            consentRecord(
                id = "00000000-0000-0000-0000-000000000032",
                subjectReference = subject,
                policyVersion = "2026-08-01",
                givenAt = Instant.parse("2026-08-01T10:00:00Z"),
            ),
        )
        repository.save(
            consentRecord(
                id = "00000000-0000-0000-0000-000000000031",
                subjectReference = subject,
                policyVersion = "2026-07-01",
                givenAt = Instant.parse("2026-07-01T10:00:00Z"),
            ).withdraw(at = Instant.parse("2026-07-15T10:00:00Z")),
        )

        val history = repository.findHistoricalByIdentity(
            workspaceScope,
            subject,
            "marketing_emails",
        ).toList()

        assertEquals(
            listOf("00000000-0000-0000-0000-000000000031", "00000000-0000-0000-0000-000000000032"),
            history.map { it.id.value },
        )
        assertEquals(ConsentStatus.WITHDRAWN, history.first().status)
        assertEquals(ConsentStatus.ACTIVE, history.last().status)
    }

    @AfterEach
    fun cleanConsentRecords() = runTest {
        databaseClient.sql("DELETE FROM consent_record_events").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM consent_records").fetch().rowsUpdated().awaitSingle()
    }

    private fun consentRecord(
        id: String = java.util.UUID.randomUUID().toString(),
        scope: ConsentScope = workspaceScope,
        subjectReference: SubjectReference = SubjectReference.workspace(workspaceScope.id.toString()),
        purpose: String = "marketing_emails",
        policyVersion: String = "2026-07-01",
        givenAt: Instant = Instant.parse("2026-07-17T10:00:00Z"),
    ): ConsentRecord = ConsentRecord(
        id = ConsentRecordId(id),
        scope = scope,
        subjectReference = subjectReference,
        consentType = ConsentType.CONSENT,
        purpose = purpose,
        policyVersion = policyVersion,
        source = "settings_update",
        locale = "es-ES",
        givenAt = givenAt,
    )

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("consent_repository")
    }
}
