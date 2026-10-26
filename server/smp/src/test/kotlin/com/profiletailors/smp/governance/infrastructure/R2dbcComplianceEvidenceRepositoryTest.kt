package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ComplianceControl
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.ComplianceEvidence
import com.profiletailors.smp.governance.domain.ComplianceEvidenceId
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptance
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceId
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
import java.util.UUID
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
    fun `native UUID control evidence links preserve semantic control keys and actor names`() = runTest {
        val controls = R2dbcComplianceControlRepository(databaseClient)
        val control = controls.save(
            ComplianceControl(ComplianceControlId(CONTROL_ID), "privacy-retention", "Privacy retention"),
        )
        val evidence = evidence(EV_REVIEW_1)
        repository.save(evidence)

        val link = repository.linkControlEvidence(control.id, evidence.id, "auditor@example.com")

        assertEquals(link.id, UUID.fromString(link.id).toString())
        assertEquals("privacy-retention", controls.findById(control.id)?.controlKey)
        assertEquals("auditor@example.com", link.linkedBy)
        assertEquals(evidence.id, repository.findByControlId(control.id).toList().single().id)
    }

    @Test
    fun `native UUID risk acceptance preserves textual scopes and accepted by`() = runTest {
        val controls = R2dbcComplianceControlRepository(databaseClient)
        val control = controls.save(
            ComplianceControl(ComplianceControlId(CONTROL_ID), "privacy-retention", "Privacy retention"),
        )
        val risks = R2dbcComplianceRiskAcceptanceRepository(databaseClient)
        val evaluatedAt = Instant.parse("2026-07-22T10:00:00Z")
        val risk = ComplianceRiskAcceptance(
            id = ComplianceRiskAcceptanceId("dddddddd-dddd-dddd-dddd-dddddddddddd"),
            controlId = control.id,
            workspaceScope = "semantic-workspace-scope",
            riskSummary = "Accepted risk",
            requestedBy = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
            acceptedBy = "reviewer@example.com",
            expiresAt = evaluatedAt.plusSeconds(3600),
        )

        risks.save(risk)

        val found = risks.activeForControl(control.id, ComplianceEvaluationContext(), evaluatedAt).toList().single()
        assertEquals(risk.id, found.id)
        assertEquals(risk.requestedBy, found.requestedBy)
        assertEquals(risk.workspaceScope, found.workspaceScope)
        assertEquals(risk.acceptedBy, found.acceptedBy)
    }

    @Test
    fun `saves and finds evidence by id including the scheduled review date`() = runTest {
        val reviewAt = Instant.parse("2027-01-15T00:00:00Z")
        val evidence = evidence(id = EV_REVIEW_1, reviewAt = reviewAt)

        repository.save(evidence)

        val found = requireNotNull(repository.findById(ComplianceEvidenceId(EV_REVIEW_1)))
        assertEquals(reviewAt, found.reviewAt)
        assertEquals(evidence.evidenceType, found.evidenceType)
        assertEquals(evidence.title, found.title)
        assertEquals(evidence.submittedBy, found.submittedBy)
    }

    @Test
    fun `saves and finds evidence with no scheduled review date`() = runTest {
        val evidence = evidence(id = EV_NO_REVIEW)

        repository.save(evidence)

        val found = requireNotNull(repository.findById(ComplianceEvidenceId(EV_NO_REVIEW)))
        assertNull(found.reviewAt)
    }

    @Test
    fun `findById returns null for an unknown evidence id`() = runTest {
        assertNull(repository.findById(ComplianceEvidenceId(EV_MISSING)))
    }

    @Test
    fun `saves and retrieves evidence links for a given evidence`() = runTest {
        val evidence = evidence(id = EV_WITH_LINKS)
        repository.save(evidence)

        val codeLink = repository.saveEvidenceLink(
            evidenceLink(id = EVLINK_CODE, evidenceId = evidence.id, linkType = EvidenceLinkType.CODE),
        )
        val testLink = repository.saveEvidenceLink(
            evidenceLink(id = EVLINK_TEST, evidenceId = evidence.id, linkType = EvidenceLinkType.TEST),
        )

        assertEquals(EVLINK_CODE, codeLink.id)
        assertEquals(EvidenceLinkType.CODE, codeLink.linkType)
        assertEquals(EVLINK_TEST, testLink.id)

        val links = repository.findLinksByEvidenceId(evidence.id).toList()

        assertEquals(2, links.size)
        assertTrue(links.any { it.id == EVLINK_CODE && it.linkType == EvidenceLinkType.CODE })
        assertTrue(links.any { it.id == EVLINK_TEST && it.linkType == EvidenceLinkType.TEST })
    }

    @Test
    fun `findLinksByEvidenceId orders links by most recently linked first`() = runTest {
        val evidence = evidence(id = EV_ORDERING)
        repository.save(evidence)

        val older = Instant.parse("2026-01-01T00:00:00Z")
        val newer = Instant.parse("2026-06-01T00:00:00Z")
        repository.saveEvidenceLink(
            evidenceLink(id = EVLINK_OLDER, evidenceId = evidence.id, linkedAt = older),
        )
        repository.saveEvidenceLink(
            evidenceLink(id = EVLINK_NEWER, evidenceId = evidence.id, linkedAt = newer),
        )

        val links = repository.findLinksByEvidenceId(evidence.id).toList()

        assertEquals(listOf(EVLINK_NEWER, EVLINK_OLDER), links.map { it.id })
    }

    @Test
    fun `findLinksByEvidenceId returns an empty flow for evidence with no links`() = runTest {
        val evidence = evidence(id = EV_NO_LINKS)
        repository.save(evidence)

        val links = repository.findLinksByEvidenceId(evidence.id).toList()

        assertTrue(links.isEmpty())
    }

    @Test
    fun `findLinksByEvidenceId only returns links for the requested evidence`() = runTest {
        val evidenceA = evidence(id = EV_SCOPE_A)
        val evidenceB = evidence(id = EV_SCOPE_B)
        repository.save(evidenceA)
        repository.save(evidenceB)

        repository.saveEvidenceLink(evidenceLink(id = EVLINK_A, evidenceId = evidenceA.id))
        repository.saveEvidenceLink(evidenceLink(id = EVLINK_B, evidenceId = evidenceB.id))

        val linksForA = repository.findLinksByEvidenceId(evidenceA.id).toList()

        assertEquals(listOf(EVLINK_A), linksForA.map { it.id })
    }

    @Test
    fun `saveEvidenceLink persists an optional description`() = runTest {
        val evidence = evidence(id = EV_WITH_DESCRIPTION)
        repository.save(evidence)

        repository.saveEvidenceLink(
            evidenceLink(
                id = EVLINK_WITH_DESC,
                evidenceId = evidence.id,
                description = "Screenshot demonstrating consent capture",
            ),
        )

        val link = repository.findLinksByEvidenceId(evidence.id).toList().first()
        assertEquals("Screenshot demonstrating consent capture", link.description)
    }

    @Test
    fun `saveEvidenceLink stores a null description when none is provided`() = runTest {
        val evidence = evidence(id = EV_WITHOUT_DESCRIPTION)
        repository.save(evidence)

        repository.saveEvidenceLink(evidenceLink(id = EVLINK_NO_DESC, evidenceId = evidence.id))

        val link = repository.findLinksByEvidenceId(evidence.id).toList().first()
        assertNull(link.description)
    }

    @AfterEach
    fun cleanEvidence() = runTest {
        databaseClient.sql("DELETE FROM compliance_risk_acceptances WHERE control_id = '$CONTROL_ID'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM compliance_control_evidences WHERE control_id = '$CONTROL_ID'")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM evidence_links").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM compliance_evidences").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM compliance_controls WHERE id = '$CONTROL_ID'")
            .fetch().rowsUpdated().awaitSingle()
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
        private const val CONTROL_ID = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        private const val EV_REVIEW_1 = "72ab88b1-0e82-589f-90a9-694de7903878"
        private const val EV_NO_REVIEW = "136b60cb-2c52-57fd-bbc0-1d6f70d59609"
        private const val EV_MISSING = "a486c0ec-bb72-5cee-9a47-c0aa00b6bff4"
        private const val EV_WITH_LINKS = "6a0af917-fba8-5dd0-ad73-c13060631fa9"
        private const val EVLINK_CODE = "e1ea9f06-8418-57ab-97ab-ea59c51b1f03"
        private const val EVLINK_TEST = "9beea47c-b2a6-5219-a1ad-9d994cfdc861"
        private const val EV_ORDERING = "31f8916b-48ae-505c-8db1-763937b56b5c"
        private const val EVLINK_OLDER = "954484a7-945a-548a-9827-190e1bf0bb0f"
        private const val EVLINK_NEWER = "2491ebe7-09a4-5f4b-a30d-1809cef4c595"
        private const val EV_NO_LINKS = "ec3e7217-849b-506d-bd87-de110312b225"
        private const val EV_SCOPE_A = "82392b76-9d3e-5ed8-9956-095e723fd8a2"
        private const val EV_SCOPE_B = "2933ae5b-3818-56f9-a724-d11efcd4052a"
        private const val EVLINK_A = "2df22577-2597-5157-888c-5a1cdebb46a9"
        private const val EVLINK_B = "112249a8-0a7a-5172-afff-fd4c58f9dcf4"
        private const val EV_WITH_DESCRIPTION = "472d3d61-687d-589a-a919-4616e88ed678"
        private const val EVLINK_WITH_DESC = "7ca99b9c-c38e-50ca-b9a6-04c20e1ee625"
        private const val EV_WITHOUT_DESCRIPTION = "383090e8-77a4-5c08-98ec-8529d5fcb0fc"
        private const val EVLINK_NO_DESC = "a38744ad-8809-5b44-98b2-54acd07105c0"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("compliance_evidence_repository")
    }
}
