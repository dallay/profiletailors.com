package com.profiletailors.smp.governance.infrastructure.persistence

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains

class GovernanceLiquibaseChangelogTest {

    @Test
    fun `master changelog includes governance compliance changelogs`() {
        val master = changelog("db.changelog-master.yaml")

        assertContains(master, "db/changelog/governance/002-create-compliance-controls.yaml")
        assertContains(master, "db/changelog/governance/003-seed-compliance-controls.yaml")
    }

    @Test
    fun `compliance schema changelog creates all required tables`() {
        val schema = changelog("governance/002-create-compliance-controls.yaml")

        assertContains(schema, "tableName: compliance_controls")
        assertContains(schema, "tableName: compliance_control_applicability_rules")
        assertContains(schema, "tableName: compliance_control_applicability_dimensions")
        assertContains(schema, "tableName: compliance_control_evidence_requirements")
        assertContains(schema, "tableName: compliance_evidences")
        assertContains(schema, "tableName: compliance_control_evidences")
        assertContains(schema, "tableName: compliance_risk_acceptances")
    }

    @Test
    fun `seed changelog creates eight bootstrap controls`() {
        val seed = changelog("governance/003-seed-compliance-controls.yaml")

        assertContains(seed, "ctrl-privacy-data-retention")
        assertContains(seed, "ctrl-valid-consent")
        assertContains(seed, "ctrl-data-subject-rights")
        assertContains(seed, "ctrl-breach-response")
        assertContains(seed, "ctrl-content-licensing")
        assertContains(seed, "ctrl-dpa")
        assertContains(seed, "ctrl-subprocessor")
        assertContains(seed, "ctrl-accessibility")
    }

    private fun changelog(relativePath: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource("db/changelog/$relativePath")) {
            "Missing changelog: db/changelog/$relativePath"
        }
        return java.nio.file.Path.of(resource.toURI()).readText()
    }
}
