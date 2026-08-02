package com.profiletailors.smp.publishing.infrastructure.persistence

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class SocialContentLiquibaseChangelogTest {
    @Test
    fun `master includes the social content foundation changelog`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")

        master shouldContain "db/changelog/publishing/016-create-social-content-foundation.yaml"
        javaClass.classLoader
            .getResource("db/changelog/publishing/016-create-social-content-foundation.yaml")
            .shouldNotBeNull()
    }

    @Test
    fun `foundation changelog defines isolated content tables and rollback`() {
        val changelog = resourceText("db/changelog/publishing/016-create-social-content-foundation.yaml")

        listOf(
            "social_content_actor_capabilities",
            "social_content_posts",
            "social_content_comments",
            "social_content_payload_cache",
            "social_content_sync_checkpoints",
            "social_content_webhook_events",
            "social_content_reply_commands",
        ).forEach { changelog shouldContain "tableName: $it" }
        changelog shouldContain "uq_social_content_posts_identity"
        changelog shouldContain "uq_social_content_comments_identity"
        changelog shouldContain "uq_social_content_reply_commands_idempotency"
        changelog shouldContain "rollback:"
        changelog shouldContain "dropTable:"
    }

    @Test
    fun `foundation changelog includes expiry checkpoint and hierarchy indexes`() {
        val changelog = resourceText("db/changelog/publishing/016-create-social-content-foundation.yaml")

        changelog shouldContain "idx_social_content_posts_workspace_actor_published"
        changelog shouldContain "idx_social_content_comments_post_parent"
        changelog shouldContain "idx_social_content_payload_cache_expires_at"
        changelog shouldContain "idx_social_content_sync_checkpoints_due"
    }

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing resource $path" }
            .bufferedReader()
            .use { it.readText() }
}
