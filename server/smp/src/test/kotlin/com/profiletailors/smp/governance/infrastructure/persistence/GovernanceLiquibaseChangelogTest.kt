package com.profiletailors.smp.governance.infrastructure.persistence

import io.kotest.matchers.string.shouldContain
import kotlin.io.path.readText
import kotlin.test.Test

class GovernanceLiquibaseChangelogTest {

    @Test
    fun `master changelog includes governance compliance changelogs`() {
        val master = changelog("db.changelog-master.yaml")

        master shouldContain "db/changelog/governance/002-create-compliance-controls.yaml"
        master shouldContain "db/changelog/governance/003-seed-compliance-controls.yaml"
    }

    @Test
    fun `compliance schema changelog creates all required tables`() {
        val schema = changelog("governance/002-create-compliance-controls.yaml")

        schema shouldContain "tableName: compliance_controls"
        schema shouldContain "tableName: compliance_control_applicability_rules"
        schema shouldContain "tableName: compliance_control_applicability_dimensions"
        schema shouldContain "tableName: compliance_control_evidence_requirements"
        schema shouldContain "tableName: compliance_evidences"
        schema shouldContain "tableName: compliance_control_evidences"
        schema shouldContain "tableName: compliance_risk_acceptances"
    }

    @Test
    fun `seed changelog creates eight bootstrap controls`() {
        val seed = changelog("governance/003-seed-compliance-controls.yaml")

        seed shouldContain "ctrl-privacy-data-retention"
        seed shouldContain "ctrl-valid-consent"
        seed shouldContain "ctrl-data-subject-rights"
        seed shouldContain "ctrl-breach-response"
        seed shouldContain "ctrl-content-licensing"
        seed shouldContain "ctrl-dpa"
        seed shouldContain "ctrl-subprocessor"
        seed shouldContain "ctrl-accessibility"
    }

    @Test
    fun `master changelog includes evidence links and review at changelog`() {
        val master = changelog("db.changelog-master.yaml")

        master shouldContain "db/changelog/governance/007-evidence-links-and-review-at.yaml"
    }

    @Test
    fun `evidence links changelog adds review_at column and creates the evidence_links table`() {
        val changelog007 = changelog("governance/007-evidence-links-and-review-at.yaml")

        changelog007 shouldContain "tableName: compliance_evidences"
        changelog007 shouldContain "name: review_at"
        changelog007 shouldContain "tableName: evidence_links"
        changelog007 shouldContain "foreignKeyName: fk_evidence_link_evidence"
        changelog007 shouldContain "references: compliance_evidences(id)"
        changelog007 shouldContain "indexName: idx_evidence_links_evidence_id"
    }

    @Test
    fun `consent ledger and permission changelogs are registered`() {
        val master = changelog("db.changelog-master.yaml")
        val ledger = changelog("governance/005-create-consent-record-events.yaml")
        val permission = changelog("authorization/010-seed-consent-permission.yaml")

        master shouldContain "db/changelog/governance/005-create-consent-record-events.yaml"
        master shouldContain "db/changelog/authorization/010-seed-consent-permission.yaml"
        ledger shouldContain "tableName: consent_record_events"
        ledger shouldContain "CREATE UNIQUE INDEX uq_consent_active"
        ledger shouldContain "WHERE status = 'ACTIVE'"
        permission shouldContain "workspace:consent:read"
        permission shouldContain "WORKSPACE_OWNER"
    }

    private fun changelog(relativePath: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource("db/changelog/$relativePath")) {
            "Missing changelog: db/changelog/$relativePath"
        }
        return java.nio.file.Path.of(resource.toURI()).readText()
    }
}
