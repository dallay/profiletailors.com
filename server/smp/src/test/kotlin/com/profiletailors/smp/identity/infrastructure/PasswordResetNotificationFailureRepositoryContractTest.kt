package com.profiletailors.smp.identity.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class PasswordResetNotificationFailureRepositoryContractTest {

    @Test
    fun `adapter SQL and migration use the same terminal failure schema`() {
        val adapter = repositoryFile(
            "server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/" +
                "R2dbcPasswordResetNotificationFailureRepository.kt",
        ).readText()
        val migration = repositoryFile(
            "server/smp/src/main/resources/db/changelog/identity/" +
                "006-create-password-reset-notification-failures.yaml",
        ).readText()

        REQUIRED_COLUMNS.forEach { column ->
            assertThat(adapter).contains(column)
            assertThat(migration).contains("name: $column")
        }
        assertThat(adapter).contains("INSERT INTO password_reset_notification_failures")
        assertThat(migration).contains("tableName: password_reset_notification_failures")
        assertThat(migration).contains("referencedTableName: user_identities")
        assertThat(migration).contains("onDelete: CASCADE")
    }

    private fun repositoryFile(relativePath: String): File = generateSequence(
        File(System.getProperty("user.dir")).absoluteFile,
    ) { it.parentFile }.map { File(it, relativePath) }.first { it.isFile }

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
