package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
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

    @Test
    fun `saves and finds consent by id`() = runTest {
        val consent = consentRecord(id = "cs-save-find")

        repository.save(consent)

        val found = repository.findById(ConsentRecordId("cs-save-find"))
        assertNotNull(found)
        assertEquals(consent.id, found.id)
        assertEquals(consent.workspaceId, found.workspaceId)
        assertEquals(consent.subjectReference, found.subjectReference)
        assertEquals(consent.consentType, found.consentType)
        assertEquals(consent.purpose, found.purpose)
        assertEquals(consent.policyVersion, found.policyVersion)
        assertEquals(consent.status, found.status)
        assertEquals(consent.givenAt, found.givenAt)
    }

    @Test
    fun `findActive is scoped by workspace to prevent cross workspace disclosure`() = runTest {
        val subject = SubjectReference.user("user-001")
        repository.save(consentRecord(id = "cs-ws-1", workspaceId = "ws-1", subjectReference = subject))
        repository.save(consentRecord(id = "cs-ws-2", workspaceId = "ws-2", subjectReference = subject))

        val ws1 = repository.findActive("ws-1", subject, "marketing_emails", "2026-07-01")
        val ws2 = repository.findActive("ws-2", subject, "marketing_emails", "2026-07-01")

        assertEquals("cs-ws-1", ws1?.id?.value)
        assertEquals("cs-ws-2", ws2?.id?.value)
    }

    @Test
    fun `findActive ignores withdrawn records`() = runTest {
        val subject = SubjectReference.workspace("ws-001")
        val withdrawn = consentRecord(id = "cs-withdrawn", subjectReference = subject)
            .withdraw(at = Instant.parse("2026-08-01T10:00:00Z"))
        repository.save(withdrawn)

        val result = repository.findActive("ws-001", subject, "marketing_emails", "2026-07-01")

        assertNull(result)
    }

    @Test
    fun `existsActive returns true only for active workspace scoped record`() = runTest {
        val subject = SubjectReference.user("user-001")
        repository.save(consentRecord(id = "cs-active", workspaceId = "ws-1", subjectReference = subject))

        assertTrue(repository.existsActive("ws-1", subject, "marketing_emails", "2026-07-01"))
        assertFalse(repository.existsActive("ws-2", subject, "marketing_emails", "2026-07-01"))
    }

    @Test
    fun `findActiveByWorkspace filters by subject kind and purpose`() = runTest {
        repository.save(consentRecord(id = "cs-user", subjectReference = SubjectReference.user("user-001")))
        repository.save(consentRecord(id = "cs-anon", subjectReference = SubjectReference.anonymous("lead-hash")))
        repository.save(
            consentRecord(
                id = "cs-terms",
                subjectReference = SubjectReference.user("user-002"),
                purpose = "terms.v1",
            ),
        )

        val marketingUsers = repository.findActiveByWorkspace(
            workspaceId = "ws-001",
            subjectKind = SubjectKind.USER,
            purpose = "marketing_emails",
        ).toList()

        assertEquals(listOf("cs-user"), marketingUsers.map { it.id.value })
    }

    @Test
    fun `findHistoricalByIdentity returns active and withdrawn history ordered by givenAt`() = runTest {
        val subject = SubjectReference.user("user-history")
        repository.save(
            consentRecord(
                id = "cs-history-2",
                subjectReference = subject,
                policyVersion = "2026-08-01",
                givenAt = Instant.parse("2026-08-01T10:00:00Z"),
            ),
        )
        repository.save(
            consentRecord(
                id = "cs-history-1",
                subjectReference = subject,
                policyVersion = "2026-07-01",
                givenAt = Instant.parse("2026-07-01T10:00:00Z"),
            ).withdraw(at = Instant.parse("2026-07-15T10:00:00Z")),
        )

        val history = repository.findHistoricalByIdentity("ws-001", subject, "marketing_emails").toList()

        assertEquals(listOf("cs-history-1", "cs-history-2"), history.map { it.id.value })
        assertEquals(ConsentStatus.WITHDRAWN, history.first().status)
        assertEquals(ConsentStatus.ACTIVE, history.last().status)
    }

    @AfterEach
    fun cleanConsentRecords() = runTest {
        databaseClient.sql("DELETE FROM consent_records").fetch().rowsUpdated().awaitSingle()
    }

    private fun consentRecord(
        id: String = "cs-001",
        workspaceId: String = "ws-001",
        subjectReference: SubjectReference = SubjectReference.workspace(workspaceId),
        purpose: String = "marketing_emails",
        policyVersion: String = "2026-07-01",
        givenAt: Instant = Instant.parse("2026-07-17T10:00:00Z"),
    ): ConsentRecord = ConsentRecord(
        id = ConsentRecordId(id),
        workspaceId = workspaceId,
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
