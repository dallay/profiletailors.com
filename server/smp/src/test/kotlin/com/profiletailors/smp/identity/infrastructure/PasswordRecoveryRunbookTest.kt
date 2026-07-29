package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.integration.support.RepositoryRoot
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class PasswordRecoveryRunbookTest {

    @Test
    fun `runbook is actionable safe and uses repository commands`() {
        val runbookPath = repositoryRoot().resolve("docs/runbooks/password-recovery.md")
        runbookPath.shouldExist()

        val runbook = runbookPath.readText()
        val justfile = repositoryRoot().resolve("justfile").readText()

        topLevelSections(runbook) shouldBe listOf(
            "Overview",
            "Changes",
            "Usage",
            "Troubleshooting",
            "References",
        )
        REQUIRED_OPERATIONAL_CONTRACT.forEach { required ->
            runbook.lowercase() shouldContain required.lowercase()
        }
        REQUIRED_JUST_RECIPES.forEach { recipe ->
            justfile shouldContain "$recipe:"
            runbook shouldContain "just $recipe"
        }

        val diagnosticSql = fencedBlock(runbook, "sql")
        FORBIDDEN_SQL_FIELDS.forEach { field ->
            diagnosticSql.lowercase() shouldNotContain field.lowercase()
        }
        diagnosticSql shouldContain "password_reset_notification_failures"
        diagnosticSql shouldContain "failure_category"
        diagnosticSql.replace(Regex("\\s+"), " ").lowercase() shouldNotContain " category as safe_category"
        diagnosticSql shouldContain "password_reset_tokens"
    }

    private fun repositoryRoot(): Path = RepositoryRoot.path()

    private fun topLevelSections(markdown: String): List<String> = markdown.lineSequence()
        .filter { it.startsWith("## ") }
        .map { it.removePrefix("## ") }
        .toList()

    private fun fencedBlock(markdown: String, language: String): String {
        val marker = "```$language"
        val start = markdown.indexOf(marker)
        start shouldBeGreaterThan -1
        val contentStart = start + marker.length
        val end = markdown.indexOf("```", contentStart)
        end shouldBeGreaterThan contentStart
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
