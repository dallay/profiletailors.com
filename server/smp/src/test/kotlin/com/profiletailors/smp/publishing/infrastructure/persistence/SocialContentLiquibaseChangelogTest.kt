package com.profiletailors.smp.publishing.infrastructure.persistence

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class SocialContentLiquibaseChangelogTest {
    @Test
    fun `master includes the social content foundation changelog`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")

        master shouldContain "\n  - include:\n      file: $FOUNDATION_CHANGELOG"
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
    fun `master includes the workspace foreign key remediation changelog`() {
        val master = resourceText("db/changelog/db.changelog-master.yaml")
        val migration = resourceText("db/changelog/publishing/017-social-content-workspace-fks.yaml")
        val checkpointMigration = resourceText("db/changelog/publishing/018-social-content-comment-checkpoints.yaml")

        master shouldContain "file: db/changelog/publishing/017-social-content-workspace-fks.yaml"
        master shouldContain "file: db/changelog/publishing/018-social-content-comment-checkpoints.yaml"
        migration shouldContain "uq_social_accounts_workspace_id"
        migration shouldContain "uq_social_content_posts_workspace_id"
        migration shouldContain "fk_social_content_capabilities_workspace_account"
        migration shouldContain "fk_social_content_posts_workspace_account"
        migration shouldContain "fk_social_content_comments_workspace_post"
        migration shouldContain "fk_social_content_checkpoints_workspace_account"
        migration shouldContain "fk_social_content_webhooks_workspace_account"
        migration shouldContain "fk_social_content_replies_workspace_account"
        checkpointMigration shouldContain "name: post_id"
        checkpointMigration shouldContain "uq_social_content_sync_checkpoints_post_identity"
        checkpointMigration shouldContain "dropUniqueConstraint:"
        checkpointMigration shouldContain "rollback:"
        migration shouldContain "dropForeignKeyConstraint:"
    }

    @Test
    fun `workspace foreign key remediation uses composite account and post references`() {
        val migration = resourceText("db/changelog/publishing/017-social-content-workspace-fks.yaml")

        migration shouldContain "baseColumnNames: workspace_id, social_account_id"
        migration shouldContain "referencedColumnNames: workspace_id, id"
        migration shouldContain "baseColumnNames: workspace_id, post_id"
        migration shouldContain "referencedTableName: social_content_posts"
    }

    @Test
    fun `foundation changelog includes expiry checkpoint and hierarchy indexes`() {
        val changelog = resourceText("db/changelog/publishing/016-create-social-content-foundation.yaml")

        changelog shouldContain "idx_social_content_posts_workspace_actor_published"
        changelog shouldContain "idx_social_content_comments_post_parent"
        changelog shouldContain "idx_social_content_payload_cache_expires_at"
        changelog shouldContain "idx_social_content_sync_checkpoints_due"
    }

    @Test
    fun `comment checkpoint migration stores external post ids without a local foreign key`() {
        val checkpointMigration = resourceText("db/changelog/publishing/018-social-content-comment-checkpoints.yaml")

        checkpointMigration shouldContain "name: post_id"
        checkpointMigration shouldContain "type: varchar(255)"
        checkpointMigration shouldNotContain "addForeignKeyConstraint:"
        checkpointMigration shouldNotContain "fk_social_content_checkpoints_workspace_post"
    }

    @Test
    fun `comment checkpoint migration rollback deletes comment checkpoints before restoring the unique constraint`() {
        val checkpointMigration = resourceText("db/changelog/publishing/018-social-content-comment-checkpoints.yaml")

        checkpointMigration shouldContain "DELETE FROM social_content_sync_checkpoints WHERE resource = 'COMMENTS'"
        checkpointMigration shouldContain "dropUniqueConstraint:"
        checkpointMigration shouldContain "addUniqueConstraint:"
        checkpointMigration shouldContain "dropColumn:"
        checkpointMigration shouldContain "columnName: post_id"
    }

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing resource $path" }
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val FOUNDATION_CHANGELOG =
            "db/changelog/publishing/016-create-social-content-foundation.yaml"
    }
}
