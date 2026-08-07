package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentReader
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, type)

@Repository
class R2dbcSocialContentRepositories(private val databaseClient: DatabaseClient) :
    SocialContentPostRepository,
    SocialContentCheckpointRepository,
    SocialContentReader {
    override suspend fun upsert(post: SocialPost): SocialPost {
        databaseClient.sql(
            """
            INSERT INTO social_content_posts (
                id, workspace_id, social_account_id, provider, external_post_id, published_at,
                last_modified_at, body, origin, local_publication_id, lifecycle, expires_at
            ) VALUES (
                :id, :workspaceId, :socialAccountId, :provider, :externalPostId, :publishedAt,
                :lastModifiedAt, :body, :origin, :localPublicationId, :lifecycle, :expiresAt
            )
            ON CONFLICT (workspace_id, provider, social_account_id, external_post_id) DO UPDATE SET
                published_at = EXCLUDED.published_at,
                last_modified_at = EXCLUDED.last_modified_at,
                body = EXCLUDED.body,
                origin = EXCLUDED.origin,
                local_publication_id = EXCLUDED.local_publication_id,
                lifecycle = EXCLUDED.lifecycle,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID().toString())
            .bind(WORKSPACE_ID_PARAMETER, post.scope.value)
            .bind(SOCIAL_ACCOUNT_ID_PARAMETER, post.actorId)
            .bind(PROVIDER_PARAMETER, post.provider.name)
            .bind("externalPostId", post.externalPostId.value)
            .bind("publishedAt", post.publishedAt)
            .bindNullable("lastModifiedAt", post.lastModifiedAt, Instant::class.java)
            .bindNullable("body", post.body, String::class.java)
            .bind("origin", post.origin.name)
            .bindNullable("localPublicationId", post.localPublicationId, String::class.java)
            .bind("lifecycle", post.lifecycle.name)
            .bind("expiresAt", post.expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return post
    }

    override suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        externalPostId: ExternalPostId,
    ): SocialPost? = databaseClient.sql(
        """
        SELECT workspace_id, social_account_id, provider, external_post_id, published_at,
               last_modified_at, body, origin, local_publication_id, lifecycle, expires_at
        FROM social_content_posts
        WHERE workspace_id = :workspaceId
          AND provider = :provider
          AND social_account_id = :socialAccountId
          AND external_post_id = :externalPostId
        """.trimIndent(),
    )
        .bind(WORKSPACE_ID_PARAMETER, scope.value)
        .bind(PROVIDER_PARAMETER, provider.name)
        .bind(SOCIAL_ACCOUNT_ID_PARAMETER, actorId)
        .bind("externalPostId", externalPostId.value)
        .map { row, _ -> row.toSocialPost() }
        .one()
        .awaitSingleOrNull()

    override suspend fun tombstoneMissing(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        seenExternalIds: Set<ExternalPostId>,
    ) {
        if (seenExternalIds.isEmpty()) return
        val placeholders = seenExternalIds.indices.joinToString(", ") { ":seen$it" }
        var statement = databaseClient.sql(
            """
            UPDATE social_content_posts
            SET lifecycle = :tombstoned, updated_at = CURRENT_TIMESTAMP
            WHERE workspace_id = :workspaceId
              AND provider = :provider
              AND social_account_id = :socialAccountId
              AND external_post_id NOT IN ($placeholders)
              AND lifecycle <> :tombstoned
            """.trimIndent(),
        )
            .bind(WORKSPACE_ID_PARAMETER, scope.value)
            .bind(PROVIDER_PARAMETER, provider.name)
            .bind(SOCIAL_ACCOUNT_ID_PARAMETER, actorId)
            .bind("tombstoned", PostLifecycle.TOMBSTONED.name)
        seenExternalIds.forEachIndexed { index, externalPostId ->
            statement = statement.bind("seen$index", externalPostId.value)
        }
        statement.fetch().rowsUpdated().awaitSingle()
    }

    override suspend fun find(
        scope: WorkspaceScope,
        actorId: String,
        resource: SyncResource,
        postId: ExternalPostId?,
    ): SyncCheckpoint? = databaseClient.sql(
        """
        SELECT workspace_id, social_account_id, resource, post_id, cursor, high_water_mark, last_successful_at
        FROM social_content_sync_checkpoints
        WHERE workspace_id = :workspaceId
          AND social_account_id = :socialAccountId
          AND resource = :resource
          AND (:postId IS NULL OR post_id = :postId)
        """.trimIndent(),
    )
        .bind(WORKSPACE_ID_PARAMETER, scope.value)
        .bind(SOCIAL_ACCOUNT_ID_PARAMETER, actorId)
        .bind(RESOURCE_PARAMETER, resource.name)
        .bindNullable(POST_ID_PARAMETER, postId?.value, String::class.java)
        .map { row, _ -> row.toSyncCheckpoint() }
        .one()
        .awaitSingleOrNull()

    override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
        databaseClient.sql(
            """
            INSERT INTO social_content_sync_checkpoints (
                id, workspace_id, social_account_id, resource, post_id, cursor, high_water_mark, last_successful_at
            ) VALUES (
                :id, :workspaceId, :socialAccountId, :resource, :postId, :cursor, :highWaterMark, :lastSuccessfulAt
            )
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID().toString())
            .bind(WORKSPACE_ID_PARAMETER, checkpoint.scope.value)
            .bind(SOCIAL_ACCOUNT_ID_PARAMETER, checkpoint.actorId)
            .bind(RESOURCE_PARAMETER, checkpoint.resource.name)
            .bindNullable(POST_ID_PARAMETER, checkpoint.postId?.value, String::class.java)
            .bindNullable(CURSOR_PARAMETER, checkpoint.cursor?.value, String::class.java)
            .bindNullable(HIGH_WATER_MARK_PARAMETER, checkpoint.highWaterMark, Instant::class.java)
            .bindNullable(LAST_SUCCESSFUL_AT_PARAMETER, checkpoint.lastSuccessfulAt, Instant::class.java)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        updateCheckpoint(checkpoint)
        return checkpoint
    }

    private suspend fun updateCheckpoint(checkpoint: SyncCheckpoint) {
        val matchesPost = checkpoint.resource != SyncResource.POSTS && checkpoint.postId != null
        val postIdClause = if (matchesPost) "AND post_id = :postId" else "AND post_id IS NULL"
        var statement = databaseClient.sql(
            """
            UPDATE social_content_sync_checkpoints
            SET cursor = :cursor, high_water_mark = :highWaterMark, last_successful_at = :lastSuccessfulAt
            WHERE workspace_id = :workspaceId
              AND social_account_id = :socialAccountId
              AND resource = :resource
              $postIdClause
            """.trimIndent(),
        )
            .bind(WORKSPACE_ID_PARAMETER, checkpoint.scope.value)
            .bind(SOCIAL_ACCOUNT_ID_PARAMETER, checkpoint.actorId)
            .bind(RESOURCE_PARAMETER, checkpoint.resource.name)
            .bindNullable(CURSOR_PARAMETER, checkpoint.cursor?.value, String::class.java)
            .bindNullable(HIGH_WATER_MARK_PARAMETER, checkpoint.highWaterMark, Instant::class.java)
            .bindNullable(LAST_SUCCESSFUL_AT_PARAMETER, checkpoint.lastSuccessfulAt, Instant::class.java)
        if (matchesPost) {
            statement = statement.bind(POST_ID_PARAMETER, checkpoint.postId.value)
        }
        statement.fetch().rowsUpdated().awaitSingle()
    }

    override suspend fun findImportedPosts(query: SocialContentCalendarQuery): SocialContentPage<SocialPost> {
        val statement = databaseClient.sql(
            """
            SELECT workspace_id, social_account_id, provider, external_post_id, published_at,
                   last_modified_at, body, origin, local_publication_id, lifecycle, expires_at
            FROM social_content_posts
            WHERE workspace_id = :workspaceId
              AND published_at >= :fromAt
              AND published_at < :toAt
              AND (:actorId IS NULL OR social_account_id = :actorId)
              AND (:lifecycle IS NULL OR lifecycle = :lifecycle)
            ORDER BY published_at, external_post_id
            LIMIT :limit
            """.trimIndent(),
        )
            .bind(WORKSPACE_ID_PARAMETER, query.scope.value)
            .bind("fromAt", query.from)
            .bind("toAt", query.to)
            .bindNullable("actorId", query.actorId, String::class.java)
            .bindNullable("lifecycle", query.lifecycle?.name, String::class.java)
            .bind("limit", query.limit)
        val items = statement.map { row, _ -> row.toSocialPost() }.all().collectList().awaitSingle()
        return SocialContentPage(items, null, items.maxOfOrNull { it.publishedAt })
    }

    private fun io.r2dbc.spi.Readable.toSocialPost(): SocialPost = SocialPost(
        scope = WorkspaceScope(get("workspace_id", String::class.java) ?: error("workspace_id missing")),
        provider = SocialProvider.valueOf(get("provider", String::class.java) ?: error("provider missing")),
        actorId = get("social_account_id", String::class.java) ?: error("social_account_id missing"),
        externalPostId = ExternalPostId(
            get("external_post_id", String::class.java) ?: error("external_post_id missing"),
        ),
        publishedAt = get("published_at", Instant::class.java) ?: error("published_at missing"),
        lastModifiedAt = get("last_modified_at", Instant::class.java),
        body = get("body", String::class.java),
        origin = PostOrigin.valueOf(get("origin", String::class.java) ?: error("origin missing")),
        localPublicationId = get("local_publication_id", String::class.java),
        lifecycle = PostLifecycle.valueOf(get("lifecycle", String::class.java) ?: error("lifecycle missing")),
        expiresAt = get("expires_at", Instant::class.java) ?: error("expires_at missing"),
    )

    private fun io.r2dbc.spi.Readable.toSyncCheckpoint(): SyncCheckpoint {
        val resource = SyncResource.valueOf(get("resource", String::class.java) ?: error("resource missing"))
        val postId = get("post_id", String::class.java)?.let(::ExternalPostId)
        return SyncCheckpoint(
            scope = WorkspaceScope(get("workspace_id", String::class.java) ?: error("workspace_id missing")),
            actorId = get("social_account_id", String::class.java) ?: error("social_account_id missing"),
            resource = resource,
            cursor = get("cursor", String::class.java)?.let(::PageCursor),
            highWaterMark = get("high_water_mark", Instant::class.java),
            lastSuccessfulAt = get("last_successful_at", Instant::class.java),
            postId = postId,
        )
    }

    private companion object {
        const val WORKSPACE_ID_PARAMETER = "workspaceId"
        const val SOCIAL_ACCOUNT_ID_PARAMETER = "socialAccountId"
        const val PROVIDER_PARAMETER = "provider"
        const val RESOURCE_PARAMETER = "resource"
        const val POST_ID_PARAMETER = "postId"
        const val CURSOR_PARAMETER = "cursor"
        const val HIGH_WATER_MARK_PARAMETER = "highWaterMark"
        const val LAST_SUCCESSFUL_AT_PARAMETER = "lastSuccessfulAt"
    }
}
