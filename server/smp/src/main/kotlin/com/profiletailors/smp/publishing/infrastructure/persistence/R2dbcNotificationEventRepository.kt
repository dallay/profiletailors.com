package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.NotificationCategory
import com.profiletailors.smp.publishing.domain.NotificationEvent
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcNotificationEventRepository(
    private val databaseClient: DatabaseClient,
    private val clock: java.time.Clock,
) : NotificationEventRepository {

    override suspend fun record(event: NotificationEvent): NotificationEvent {
        val id = event.id.ifEmpty { "nevt-${java.util.UUID.randomUUID()}" }
        val now = clock.instant()
        val toPersist = event.copy(id = id, createdAt = event.createdAt ?: now)

        databaseClient.sql(
            """
            INSERT INTO notification_events (
                id, workspace_id, provider, social_account_id, publication_id,
                category, message, suggested_action, public_url, occurred_at, created_at
            ) VALUES (
                :id, :workspaceId, :provider, :socialAccountId, :publicationId,
                :category, :message, :suggestedAction, :publicUrl, :occurredAt, :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", toPersist.id)
            .bind("workspaceId", toPersist.workspaceId)
            .bind("provider", toPersist.provider.name)
            .bind("socialAccountId", toPersist.socialAccountId)
            .bindNullable("publicationId", toPersist.publicationId, String::class.java)
            .bind("category", toPersist.category.name)
            .bind("message", toPersist.message)
            .bindNullable("suggestedAction", toPersist.suggestedAction, String::class.java)
            .bindNullable("publicUrl", toPersist.publicUrl, String::class.java)
            .bind("occurredAt", toPersist.occurredAt)
            .bind("createdAt", toPersist.createdAt ?: now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return toPersist
    }

    override suspend fun findByWorkspace(
        workspaceId: String,
        socialAccountId: String?,
        publicationId: String?,
        categories: Set<NotificationCategory>?,
        limit: Int,
    ): List<NotificationEvent> {
        val conditions = mutableListOf("workspace_id = :workspaceId")
        val paramKeys = mutableMapOf<String, Any>(
            "workspaceId" to workspaceId,
        )

        socialAccountId?.let {
            conditions.add("social_account_id = :socialAccountId")
            paramKeys["socialAccountId"] = it
        }

        publicationId?.let {
            conditions.add("publication_id = :publicationId")
            paramKeys["publicationId"] = it
        }

        categories?.takeIf { it.isNotEmpty() }?.let { set ->
            val placeholders = set.mapIndexed { i, cat ->
                val key = "category_$i"
                paramKeys[key] = cat.name
                ":$key"
            }
            conditions.add("category IN (${placeholders.joinToString(", ")})")
        }

        val sql = """
            SELECT id, workspace_id, provider, social_account_id, publication_id,
                   category, message, suggested_action, public_url, occurred_at, created_at
            FROM notification_events
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY occurred_at DESC
            LIMIT :limit
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("workspaceId", paramKeys["workspaceId"]!!)
            .bind("limit", limit)

        socialAccountId?.let { spec = spec.bind("socialAccountId", it) }
        publicationId?.let { spec = spec.bind("publicationId", it) }
        categories?.forEachIndexed { i, _ ->
            spec = spec.bind("category_$i", paramKeys["category_$i"]!!)
        }

        return spec.map { row, _ ->
            NotificationEvent(
                id = requireNotNull(row.get("id", String::class.java)),
                workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                provider = SocialProvider.valueOf(requireNotNull(row.get("provider", String::class.java))),
                socialAccountId = requireNotNull(row.get("social_account_id", String::class.java)),
                publicationId = row.get("publication_id", String::class.java),
                category = NotificationCategory.valueOf(requireNotNull(row.get("category", String::class.java))),
                message = requireNotNull(row.get("message", String::class.java)),
                suggestedAction = row.get("suggested_action", String::class.java),
                publicUrl = row.get("public_url", String::class.java),
                occurredAt = requireNotNull(row.get("occurred_at", OffsetDateTime::class.java)).toInstant(),
                createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
            .all()
            .collectList()
            .awaitSingle()
    }
}
