package com.profiletailors.smp.publishing.infrastructure.persistence

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import liquibase.changelog.ChangeLogParameters
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Test

class PublishingClaimFencingLiquibaseChangelogTest {
    @Test
    fun `claim fencing migration maps existing delivery attempts to the finalization phase`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")
        val migration = resourceText(CHANGELOG)

        master shouldContain "file: $CHANGELOG"
        migration shouldContain "defaultValue: FINALIZATION"
        migration shouldNotContain "defaultValue: COMPLETED"
        migration shouldContain "rollback:"
        migration shouldContain "idx_publication_jobs_claimed_lease"
        migration shouldContain "CREATE INDEX CONCURRENTLY IF NOT EXISTS"
    }

    @Test
    fun `claim fencing migration is parseable by Liquibase`() {
        val resourceAccessor = ClassLoaderResourceAccessor()

        ChangeLogParserFactory.getInstance()
            .getParser(CHANGELOG, resourceAccessor)
            .parse(CHANGELOG, ChangeLogParameters(), resourceAccessor)
    }

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing resource $path" }
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val CHANGELOG = "db/changelog/publishing/020-add-publishing-claim-fencing-and-idempotency.yaml"
    }
}
