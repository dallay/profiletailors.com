package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailure
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcPasswordResetNotificationFailureRepositoryPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    @Test
    fun `record persists only the terminal failure contract`() = runTest {
        seedPrincipal()
        val repository = R2dbcPasswordResetNotificationFailureRepository(databaseClient)

        repository.record(
            PasswordResetNotificationFailure(
                principalId = PRINCIPAL_ID,
                notificationType = "PASSWORD_RESET",
                attempts = 3,
                failedAt = Instant.parse("2026-07-29T12:00:00Z"),
                category = EmailFailureCategory.PROVIDER_REJECTED,
            ),
        )

        val row = databaseClient.sql(
            """
            SELECT principal_id, notification_type, attempts, failed_at, failure_category
            FROM password_reset_notification_failures
            """.trimIndent(),
        ).map { result, _ ->
            listOf(
                result.get("principal_id", String::class.java),
                result.get("notification_type", String::class.java),
                result.get("attempts", Int::class.javaObjectType)?.toString(),
                result.get("failure_category", String::class.java),
            )
        }.one().awaitSingle()

        assertThat(row).containsExactly(PRINCIPAL_ID, "PASSWORD_RESET", "3", "PROVIDER_REJECTED")
    }

    private suspend fun seedPrincipal() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', 'local:terminal@example.com', NULL, 'terminal')
            """.trimIndent(),
        ).bind("id", PRINCIPAL_ID).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES (:id, 'terminal@example.com', 'terminal')
            """.trimIndent(),
        ).bind("id", PRINCIPAL_ID).fetch().rowsUpdated().awaitSingle()
    }

    private companion object {
        const val PRINCIPAL_ID = "principal-terminal-failure"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("password_reset_terminal_failure")
    }
}
