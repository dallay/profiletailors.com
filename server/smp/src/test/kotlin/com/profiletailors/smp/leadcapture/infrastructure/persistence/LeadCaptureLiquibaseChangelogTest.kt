package com.profiletailors.smp.leadcapture.infrastructure.persistence

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LeadCaptureLiquibaseChangelogTest {

    @Test
    fun `master changelog includes lead capture schema and seed changelogs`() {
        val master = changelog("db.changelog-master.yaml")

        assertContains(master, "db/changelog/lead-capture/001-create-waitlists.yaml")
        assertContains(master, "db/changelog/lead-capture/002-seed-profile-tailors-launch.yaml")
    }

    @Test
    fun `schema changelog creates waitlists and entries with per waitlist email dedupe`() {
        val schema = changelog("lead-capture/001-create-waitlists.yaml")

        assertContains(schema, "tableName: waitlists")
        assertContains(schema, "tableName: waitlist_entries")
        assertContains(schema, "columnNames: waitlist_id, normalized_email")
        assertContains(schema, "constraintName: uq_waitlist_entries_waitlist_email")
        assertContains(schema, "indexName: idx_waitlist_entries_waitlist_joined_at")
        assertContains(schema, "indexName: idx_waitlist_entries_normalized_email")
        assertContains(schema, "indexName: idx_waitlist_entries_status")
        assertContains(schema, "indexName: idx_waitlist_entries_source")
        assertContains(schema, "indexName: idx_waitlist_entries_form_id")
    }

    @Test
    fun `seed changelog creates profile tailors launch waitlist as active`() {
        val seed = changelog("lead-capture/002-seed-profile-tailors-launch.yaml")

        assertContains(seed, "profile-tailors-launch")
        assertContains(seed, "Profile Tailors Launch")
        assertContains(seed, "value: ACTIVE")
        assertTrue(seed.contains("context") && seed.contains("profile-tailors"))
    }

    private fun changelog(relativePath: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource("db/changelog/$relativePath")) {
            "Missing changelog: db/changelog/$relativePath"
        }
        return java.nio.file.Path.of(resource.toURI()).readText()
    }
}
