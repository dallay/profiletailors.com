package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ComplianceEvidence
import com.profiletailors.smp.governance.domain.ComplianceEvidenceId
import com.profiletailors.smp.governance.domain.EvidenceLink
import com.profiletailors.smp.governance.domain.EvidenceLinkType
import com.profiletailors.smp.governance.domain.EvidenceReviewStatus
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcComplianceEvidenceRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val repository by lazy { R2dbcComplianceEvidenceRepository(databaseClient) }

    @Test
    fun `saves and finds evidence by id including the scheduled review date`() = runTest {
        val reviewAt = Instant.parse("2027-01-15T00:00:00Z")
        val evidence = evidence(id = "ev-review-1", reviewAt = reviewAt)

        repository.save(evidence)

        val found = requireNotNull(repository.findById(ComplianceEvidenceId("ev-review-1")))
        assertEquals(reviewAt, found.reviewAt)
        assertEquals(evidence.evidenceType, found.evidenceType)
        assertEquals(evidence.title, found.title)
        assertEquals(evidence.submittedBy, found.submittedBy)
    }

    @Test
    fun `saves and finds evidence with no scheduled review date`() = runTest {
        val evidence = evidence(id = "ev-no-review")

        repository.save(evidence)

        val found = requireNotNull(repository.findById(ComplianceEvidenceId("ev-no-review")))
        assertNull(found.reviewAt)
    }

    @Test
    fun `findById returns null for an unknown evidence id`() = runTest {
        assertNull(repository.findById(ComplianceEvidenceId("ev-missing")))
    }

    @Test
    fun `saves and retrieves evidence links for a given evidence`() = runTest {
        val evidence = evidence(id = "ev-with-links")
        repository.save(evidence)

        val codeLink = repository.saveEvidenceLink(
            evidenceLink(id = "evlink-code", evidenceId = evidence.id, linkType = EvidenceLinkType.CODE),
        )
        val testLink = repository.saveEvidenceLink(
            evidenceLink(id = "evlink-test", evidenceId = evidence.id, linkType = EvidenceLinkType.TEST),
        )

        assertEquals("evlink-code", codeLink.id)
        assertEquals(EvidenceLinkType.CODE, codeLink.linkType)
        assertEquals("evlink-test", testLink.id)

        val links = repository.findLinksByEvidenceId(evidence.id).toList()

        assertEquals(2, links.size)
        assertTrue(links.any { it.id == "evlink-code" && it.linkType == EvidenceLinkType.CODE })
        assertTrue(links.any { it.id == "evlink-test" && it.linkType == EvidenceLinkType.TEST })
    }

    @Test
    fun `findLinksByEvidenceId orders links by most recently linked first`() = runTest {
        val evidence = evidence(id = "ev-ordering")
        repository.save(evidence)

        val older = Instant.parse("2026-01-01T00:00:00Z")
        val newer = Instant.parse("2026-06-01T00:00:00Z")
        repository.saveEvidenceLink(
            evidenceLink(id = "evlink-older", evidenceId = evidence.id, linkedAt = older),
        )
        repository.saveEvidenceLink(
            evidenceLink(id = "evlink-newer", evidenceId = evidence.id, linkedAt = newer),
        )

        val links = repository.findLinksByEvidenceId(evidence.id).toList()

        assertEquals(listOf("evlink-newer", "evlink-older"), links.map { it.id })
    }

    @Test
    fun `findLinksByEvidenceId returns an empty flow for evidence with no links`() = runTest {
        val evidence = evidence(id = "ev-no-links")
        repository.save(evidence)

        val links = repository.findLinksByEvidenceId(evidence.id).toList()

        assertTrue(links.isEmpty())
    }

    @Test
    fun `findLinksByEvidenceId only returns links for the requested evidence`() = runTest {
        val evidenceA = evidence(id = "ev-scope-a")
        val evidenceB = evidence(id = "ev-scope-b")
        repository.save(evidenceA)
        repository.save(evidenceB)

        repository.saveEvidenceLink(evidenceLink(id = "evlink-a", evidenceId = evidenceA.id))
        repository.saveEvidenceLink(evidenceLink(id = "evlink-b", evidenceId = evidenceB.id))

        val linksForA = repository.findLinksByEvidenceId(evidenceA.id).toList()

        assertEquals(listOf("evlink-a"), linksForA.map { it.id })
    }

    @Test
    fun `saveEvidenceLink persists an optional description`() = runTest {
        val evidence = evidence(id = "ev-with-description")
        repository.save(evidence)

        repository.saveEvidenceLink(
            evidenceLink(
                id = "evlink-with-desc",
                evidenceId = evidence.id,
                description = "Screenshot demonstrating consent capture",
            ),
        )

        val link = repository.findLinksByEvidenceId(evidence.id).toList().first()
        assertEquals("Screenshot demonstrating consent capture", link.description)
    }

    @Test
    fun `saveEvidenceLink stores a null description when none is provided`() = runTest {
        val evidence = evidence(id = "ev-without-description")
        repository.save(evidence)

        repository.saveEvidenceLink(evidenceLink(id = "evlink-no-desc", evidenceId = evidence.id))

        val link = repository.findLinksByEvidenceId(evidence.id).toList().first()
        assertNull(link.description)
    }

    @AfterEach
    fun cleanEvidence() = runTest {
        databaseClient.sql("DELETE FROM evidence_links").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM compliance_evidences").fetch().rowsUpdated().awaitSingle()
    }

    private fun evidence(
        id: String,
        submittedBy: String = "auditor@example.com",
        reviewAt: Instant? = null,
    ): ComplianceEvidence = ComplianceEvidence(
        id = ComplianceEvidenceId(id),
        evidenceType = "POLICY_DOCUMENT",
        title = "Data Retention Policy",
        submittedBy = submittedBy,
        reviewStatus = EvidenceReviewStatus.PENDING,
        reviewAt = reviewAt,
    )

    private fun evidenceLink(
        id: String,
        evidenceId: ComplianceEvidenceId,
        linkType: EvidenceLinkType = EvidenceLinkType.CODE,
        targetReference: String = "server/smp/src/main/kotlin/com/profiletailors/smp/example/Example.kt",
        description: String? = null,
        linkedBy: String = "engineer@example.com",
        linkedAt: Instant = Instant.parse("2026-07-22T10:00:00Z"),
    ): EvidenceLink = EvidenceLink(
        id = id,
        evidenceId = evidenceId,
        linkType = linkType,
        targetReference = targetReference,
        description = description,
        linkedBy = linkedBy,
        linkedAt = linkedAt,
    )

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("compliance_evidence_repository")
    }
}
