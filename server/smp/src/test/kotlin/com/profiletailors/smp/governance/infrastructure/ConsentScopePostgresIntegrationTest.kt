package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.application.RecordConsentCommand
import com.profiletailors.smp.governance.application.RecordConsentHandler
import com.profiletailors.smp.governance.application.WithdrawConsentCommand
import com.profiletailors.smp.governance.application.WithdrawConsentHandler
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentScopePostgresIntegrationTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer
    private val repository by lazy { R2dbcConsentRepository(databaseClient, transactionalOperator) }
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val firstWaitlist = ConsentScope.Waitlist(id)
    private val secondWaitlist = ConsentScope.Waitlist(UUID.fromString("00000000-0000-0000-0000-000000000102"))
    private val workspace = ConsentScope.Workspace(id)
    private val givenAt = Instant.parse("2026-10-26T10:00:00Z")
    private val subject = SubjectReference.anonymous("opaque-email-hash")
    private val handler by lazy { RecordConsentHandler(repository, clockAt(givenAt)) }

    @BeforeEach
    fun seedScopeOwners() = runTest {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES (:id, 'Consent Workspace', 'ACTIVE')")
            .bind("id", workspace.id).fetch().rowsUpdated().awaitSingle()
        listOf(firstWaitlist to "first-public-list", secondWaitlist to "second-public-list").forEach { (scope, key) ->
            databaseClient.sql(
                """
                INSERT INTO waitlists (id, key, name, context, status)
                VALUES (:id, :key, :key, 'profile-tailors', 'ACTIVE')
                """.trimIndent(),
            ).bind("id", scope.id).bind("key", key).fetch().rowsUpdated().awaitSingle()
        }
    }

    @AfterEach
    fun cleanConsentEvidence() = runTest {
        databaseClient.sql("DELETE FROM consent_record_events").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM consent_records").fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `public waitlist records use the real list UUID without a tenant workspace`() = runTest {
        val outcome = handler.handle(command(firstWaitlist))

        assertTrue(outcome.created)
        assertEquals(firstWaitlist, outcome.record.scope)
        assertEquals(subject, outcome.record.subjectReference)
        assertEquals(outcome.record, repository.findById(firstWaitlist, outcome.record.id))
        assertNull(repository.findById(workspace, outcome.record.id))
        val event = events(firstWaitlist).single()
        assertNull(event.workspaceId)
        assertEquals(firstWaitlist.id, event.waitlistId)
        assertEquals(UUID.fromString(outcome.record.id.value), event.consentId)
    }

    @Test
    fun `concurrent equivalent waitlist recording creates one active projection and one event`() = runTest {
        val start = CompletableDeferred<Unit>()
        val outcomes = coroutineScope {
            val pending = List(2) {
                async {
                    start.await()
                    handler.handle(command(firstWaitlist))
                }
            }
            start.complete(Unit)
            pending.awaitAll()
        }

        assertEquals(1, outcomes.count { it.created })
        assertEquals(1, outcomes.map { it.record.id }.distinct().size)
        assertEquals(1, repository.findActiveByScope(firstWaitlist).toList().size)
        assertEquals(1, events(firstWaitlist).size)
        val repeated = handler.handle(command(firstWaitlist))
        assertFalse(repeated.created)
        assertEquals(outcomes.first().record.id, repeated.record.id)
        val (insertedAgain, sameRecord) = repository.recordActiveReturning(repeated.record)
        assertFalse(insertedAgain)
        assertEquals(repeated.record, sameRecord)
        assertEquals(1, events(firstWaitlist).size)
    }

    @Test
    fun `two lists and a workspace isolate reads and withdrawals for the same subject and purpose`() = runTest {
        val records = listOf(firstWaitlist, secondWaitlist, workspace).associateWith {
            handler.handle(command(it)).record
        }
        val waitlistRecord = records.getValue(firstWaitlist)
        val workspaceRecord = records.getValue(workspace)
        val secondRecord = records.getValue(secondWaitlist)

        assertEquals(listOf(workspaceRecord), repository.findActiveByScope(workspace).toList())
        assertEquals(listOf(waitlistRecord), repository.findActiveByScope(firstWaitlist).toList())
        assertNull(repository.findById(secondWaitlist, waitlistRecord.id))
        assertNull(repository.findById(workspace, waitlistRecord.id))
        val withdrawn = withdraw(firstWaitlist)

        assertEquals(waitlistRecord.id, withdrawn.id)
        assertEquals(waitlistRecord.givenAt, withdrawn.givenAt)
        assertEquals(ConsentStatus.WITHDRAWN, withdrawn.status)
        assertNull(repository.findActive(firstWaitlist, subject, "waitlist.early_access", "2026-10-26"))
        assertEquals(
            secondRecord,
            repository.findActive(secondWaitlist, subject, "waitlist.early_access", "2026-10-26"),
        )
        assertEquals(workspaceRecord, repository.findActive(workspace, subject, "waitlist.early_access", "2026-10-26"))
        assertEquals(
            listOf(withdrawn),
            repository.findHistoricalByIdentity(firstWaitlist, subject, "waitlist.early_access").toList(),
        )
        assertEquals(listOf("ACTIVE", "WITHDRAWN"), events(firstWaitlist).map { it.status })
        assertEquals(listOf("ACTIVE"), events(secondWaitlist).map { it.status })
        assertEquals(listOf("ACTIVE"), events(workspace).map { it.status })
    }

    @Test
    fun `renewed waitlist consent preserves withdrawn history and append only event scope`() = runTest {
        val first = handler.handle(command(firstWaitlist)).record
        val withdrawn = withdraw(firstWaitlist)
        val renewed = RecordConsentHandler(repository, clockAt(givenAt.plusSeconds(120))).handle(command(firstWaitlist))

        assertTrue(renewed.created)
        assertNotEquals(first.id, renewed.record.id)
        assertEquals(
            listOf(withdrawn, renewed.record),
            repository.findHistoricalByIdentity(
                firstWaitlist,
                subject,
                "waitlist.early_access",
            ).toList(),
        )
        val evidence = events(firstWaitlist)
        assertEquals(listOf("ACTIVE", "WITHDRAWN", "ACTIVE"), evidence.map { it.status })
        assertTrue(evidence.all { it.workspaceId == null && it.waitlistId == firstWaitlist.id })
        assertEquals(2, evidence.count { it.consentId == UUID.fromString(first.id.value) })
        assertEquals(1, evidence.count { it.consentId == UUID.fromString(renewed.record.id.value) })
    }

    @Test
    fun `an existing record cannot be mutated through another scope`() = runTest {
        val original = handler.handle(command(firstWaitlist)).record

        assertThrows<IllegalStateException> {
            repository.save(original.withdraw(givenAt.plusSeconds(60)).copy(scope = secondWaitlist))
        }

        assertEquals(original, repository.findById(firstWaitlist, original.id))
        assertNull(repository.findById(secondWaitlist, original.id))
        assertEquals(listOf("ACTIVE"), events(firstWaitlist).map { it.status })
        assertTrue(events(secondWaitlist).isEmpty())
    }

    private fun command(scope: ConsentScope) = RecordConsentCommand(
        scope = scope,
        subjectReference = subject,
        consentType = ConsentType.CONSENT,
        purpose = "waitlist.early_access",
        policyVersion = "2026-10-26",
        source = "public-waitlist",
        locale = "en",
    )

    private suspend fun withdraw(scope: ConsentScope): ConsentRecord = WithdrawConsentHandler(
        repository,
        clockAt(givenAt.plusSeconds(60)),
    ).handle(WithdrawConsentCommand(scope, subject, "waitlist.early_access", "2026-10-26", "subject request"))

    private fun clockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

    private suspend fun events(scope: ConsentScope): List<ConsentEvent> {
        val column = when (scope) {
            is ConsentScope.Workspace -> "workspace_id"
            is ConsentScope.Waitlist -> "waitlist_id"
        }
        return databaseClient.sql(
            """
            SELECT workspace_id, waitlist_id, consent_id, status FROM consent_record_events
            WHERE $column = :id ORDER BY event_at
            """.trimIndent(),
        ).bind("id", scope.id).map { row, _ ->
            ConsentEvent(
                row.get("workspace_id", UUID::class.java),
                row.get("waitlist_id", UUID::class.java),
                requireNotNull(row.get("consent_id", UUID::class.java)),
                requireNotNull(row.get("status", String::class.java)),
            )
        }.all().collectList().awaitSingle()
    }

    private data class ConsentEvent(
        val workspaceId: UUID?,
        val waitlistId: UUID?,
        val consentId: UUID,
        val status: String,
    )

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("consent_scope_integration")
    }
}
