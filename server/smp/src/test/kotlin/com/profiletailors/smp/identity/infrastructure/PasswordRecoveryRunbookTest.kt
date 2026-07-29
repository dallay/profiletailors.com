package com.profiletailors.smp.identity.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class PasswordRecoveryRunbookTest {

    @Test
    fun `runbook is actionable safe and uses repository commands`() {
        val runbookPath = repositoryRoot().resolve("docs/runbooks/password-recovery.md")
        assertThat(runbookPath).exists()

        val runbook = runbookPath.readText()
        val justfile = repositoryRoot().resolve("justfile").readText()

        assertThat(topLevelSections(runbook)).containsExactly(
            "Overview",
            "Changes",
            "Usage",
            "Troubleshooting",
            "References",
        )
        REQUIRED_OPERATIONAL_CONTRACT.forEach { required ->
            assertThat(runbook).containsIgnoringCase(required)
        }
        REQUIRED_JUST_RECIPES.forEach { recipe ->
            assertThat(justfile).contains("$recipe:")
            assertThat(runbook).contains("just $recipe")
        }

        val diagnosticSql = fencedBlock(runbook, "sql")
        FORBIDDEN_SQL_FIELDS.forEach { field ->
            assertThat(diagnosticSql).doesNotContainIgnoringCase(field)
        }
        assertThat(diagnosticSql).contains("password_reset_notification_failures")
        assertThat(diagnosticSql).contains("failure_category")
        assertThat(diagnosticSql).doesNotContain("       category AS safe_category")
        assertThat(diagnosticSql).contains("password_reset_tokens")
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) {
        it.parent
    }.firstOrNull { Files.isRegularFile(it.resolve("justfile")) }
        ?: error("Repository root containing justfile was not found")

    private fun topLevelSections(markdown: String): List<String> = markdown.lineSequence()
        .filter { it.startsWith("## ") }
        .map { it.removePrefix("## ") }
        .toList()

    private fun fencedBlock(markdown: String, language: String): String {
        val marker = "```$language"
        val start = markdown.indexOf(marker)
        assertThat(start).isGreaterThanOrEqualTo(0)
        val contentStart = start + marker.length
        val end = markdown.indexOf("```", contentStart)
        assertThat(end).isGreaterThan(contentStart)
        return markdown.substring(contentStart, end)
    }

    private companion object {
        val REQUIRED_OPERATIONAL_CONTRACT = listOf(
            "Symptoms",
            "identity.password.recovery.outcomes",
            "identity.password.recovery",
            "operation",
            "notification.type",
            "status",
            "failure.category",
            "attempt.bucket",
            "app.identity.password-recovery.notification-retry.max-attempts",
            "app.identity.password-recovery.notification-retry.initial-backoff",
            "app.identity.password-recovery.notification-retry.multiplier",
            "app.identity.password-recovery.notification-retry.max-backoff",
            "Terminal failure",
            "principal_id",
            "notification_type",
            "attempts",
            "failed_at",
            "failure_category",
            "app.identity.password-recovery.cleanup.retention",
            "app.identity.password-recovery.cleanup.interval",
            "app.identity.password-recovery.cleanup.initial-delay",
            "app.identity.password-recovery.enabled=false",
            "raw token",
            "email",
            "raw IP",
            "reset URL",
            "Escalation",
        )
        val REQUIRED_JUST_RECIPES = listOf("backend-test-fast", "backend-lint")
        val FORBIDDEN_SQL_FIELDS = listOf(
            "token_hash",
            "request_ip_hash",
            "user_agent_hash",
            "email",
            "reset_url",
        )
    }
}
