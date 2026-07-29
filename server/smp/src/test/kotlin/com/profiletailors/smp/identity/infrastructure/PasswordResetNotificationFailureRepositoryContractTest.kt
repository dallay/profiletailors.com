package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.integration.support.RepositoryRoot
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PasswordResetNotificationFailureRepositoryContractTest {

    @Test
    fun `adapter SQL and migration use the same terminal failure schema`() {
        val adapterPath = repositoryRoot().resolve(
            "server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/" +
                "R2dbcPasswordResetNotificationFailureRepository.kt",
        )
        val migrationPath = repositoryRoot().resolve(
            "server/smp/src/main/resources/db/changelog/identity/" +
                "006-create-password-reset-notification-failures.yaml",
        )
        adapterPath.shouldExist()
        migrationPath.shouldExist()

        val adapter = Files.readString(adapterPath)
        val migration = Files.readString(migrationPath)

        REQUIRED_COLUMNS.forEach { column ->
            adapter shouldContain column
            migration shouldContain "name: $column"
        }
        adapter shouldContain "INSERT INTO password_reset_notification_failures"
        migration shouldContain "tableName: password_reset_notification_failures"
        migration shouldContain "referencedTableName: user_identities"
        migration shouldContain "onDelete: CASCADE"
    }

    private fun repositoryRoot(): Path = RepositoryRoot.path()

    private companion object {
        val REQUIRED_COLUMNS = listOf(
            "principal_id",
            "notification_type",
            "attempts",
            "failed_at",
            "failure_category",
        )
    }
}
