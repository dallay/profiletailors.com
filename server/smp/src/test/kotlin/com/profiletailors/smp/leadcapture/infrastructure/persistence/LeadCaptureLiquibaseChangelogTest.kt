package com.profiletailors.smp.leadcapture.infrastructure.persistence

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.io.path.readText
import kotlin.test.Test

class LeadCaptureLiquibaseChangelogTest {

    @Test
    fun `master changelog includes lead capture schema and seed changelogs`() {
        val master = changelog("db.changelog-master.yaml")

        master shouldContain "db/changelog/lead-capture/001-create-waitlists.yaml"
        master shouldContain "db/changelog/lead-capture/002-seed-profile-tailors-launch.yaml"
    }

    @Test
    fun `schema changelog creates waitlists and entries with per waitlist email dedupe`() {
        val schema = changelog("lead-capture/001-create-waitlists.yaml")

        schema shouldContain "tableName: waitlists"
        schema shouldContain "tableName: waitlist_entries"
        schema shouldContain "columnNames: waitlist_id, normalized_email"
        schema shouldContain "constraintName: uq_waitlist_entries_waitlist_email"
        schema shouldContain "indexName: idx_waitlist_entries_waitlist_joined_at"
        schema shouldContain "indexName: idx_waitlist_entries_normalized_email"
        schema shouldContain "indexName: idx_waitlist_entries_status"
        schema shouldContain "indexName: idx_waitlist_entries_source"
        schema shouldContain "indexName: idx_waitlist_entries_form_id"
    }

    @Test
    fun `seed changelog creates profile tailors launch waitlist as active`() {
        val seed = changelog("lead-capture/002-seed-profile-tailors-launch.yaml")

        seed shouldContain "profile-tailors-launch"
        seed shouldContain "Profile Tailors Launch"
        seed shouldContain "value: ACTIVE"
        (seed.contains("context") && seed.contains("profile-tailors")) shouldBe true
    }

    private fun changelog(relativePath: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource("db/changelog/$relativePath")) {
            "Missing changelog: db/changelog/$relativePath"
        }
        return java.nio.file.Path.of(resource.toURI()).readText()
    }
}
