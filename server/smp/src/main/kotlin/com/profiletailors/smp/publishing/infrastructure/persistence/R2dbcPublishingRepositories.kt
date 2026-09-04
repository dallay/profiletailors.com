package com.profiletailors.smp.publishing.infrastructure.persistence

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.DateCount
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptPhase
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.StaleJob
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

private const val PUBLICATION_UPDATE_COLUMNS = """
    workspace_id = :workspaceId,
    author_principal_id = :authorPrincipalId,
    provider = :provider,
    social_account_id = :socialAccountId,
    status = :status,
    schedule_mode = :scheduleMode,
    priority = :priority,
    title = :title,
    body_text = :bodyText,
    public_url = :publicUrl,
    blocked_at = :blockedAt,
    blocked_reason = :blockedReason,
    retry_count = :retryCount,
    scheduled_for = :scheduledFor,
    next_slot_after = :nextSlotAfter,
    published_at = :publishedAt,
    failed_at = :failedAt,
    external_publication_id = :externalPublicationId,
    last_error_code = :lastErrorCode,
    last_error_message = :lastErrorMessage,
    updated_at = :updatedAt
"""

private const val PUBLICATION_INSERT_COLUMNS = """
    id, workspace_id, author_principal_id, provider, social_account_id, status, schedule_mode, priority,
    title, body_text, public_url, blocked_at, blocked_reason, retry_count,
    scheduled_for, next_slot_after, published_at, failed_at, external_publication_id,
    last_error_code, last_error_message, created_at, updated_at
"""

private const val PUBLICATION_INSERT_VALUES = """
    :id, :workspaceId, :authorPrincipalId, :provider, :socialAccountId, :status, :scheduleMode, :priority,
    :title, :bodyText, :publicUrl, :blockedAt, :blockedReason, :retryCount,
    :scheduledFor, :nextSlotAfter, :publishedAt, :failedAt, :externalPublicationId,
    :lastErrorCode, :lastErrorMessage, :createdAt, :updatedAt
"""

private const val PUBLICATION_ID_COLUMN = "publication_id"

@Repository
@Suppress("TooManyFunctions", "StringLiteralDuplication")
class R2dbcPublicationRepository(
    private val databaseClient: DatabaseClient,
    private val transactionalOperator: TransactionalOperator,
) : PublicationRepository {
    override suspend fun createDraft(draft: PublicationDraft): PublicationDraft {
        insertOrUpdate(draft)
        replaceAssetLinks(draft)
        return draft
    }

    override suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft = createDraft(draft)

    override suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft? {
        val publication = databaseClient.sql(
            """
            SELECT id, workspace_id, author_principal_id, provider, social_account_id, status, schedule_mode, priority,
                   title, body_text, public_url, blocked_at, blocked_reason, retry_count,
                   scheduled_for, next_slot_after, published_at, failed_at, external_publication_id,
                   last_error_code, last_error_message, created_at, updated_at
            FROM publications
            WHERE workspace_id = :workspaceId AND id = :id
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("id", publicationId)
            .map { row, _ ->
                PublicationDraft(
                    id = requireNotNull(row.get("id", String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    authorPrincipalId = requireNotNull(row.get("author_principal_id", String::class.java)),
                    provider = SocialProvider.valueOf(requireNotNull(row.get("provider", String::class.java))),
                    socialAccountId = requireNotNull(row.get("social_account_id", String::class.java)),
                    status = PublicationStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                    scheduleMode = ScheduleMode.valueOf(requireNotNull(row.get("schedule_mode", String::class.java))),
                    priority = requireNotNull(row.get("priority", Boolean::class.javaObjectType)),
                    title = row.get("title", String::class.java),
                    bodyText = row.get("body_text", String::class.java),
                    publicUrl = row.get("public_url", String::class.java),
                    blockedAt = row.get("blocked_at", OffsetDateTime::class.java)?.toInstant(),
                    blockedReason = row.get("blocked_reason", String::class.java),
                    retryCount = row.get("retry_count", Int::class.javaObjectType) ?: 0,
                    assetIds = emptyList(),
                    scheduledFor = row.get("scheduled_for", OffsetDateTime::class.java)?.toInstant(),
                    nextSlotAfter = row.get("next_slot_after", OffsetDateTime::class.java)?.toInstant(),
                    publishedAt = row.get("published_at", OffsetDateTime::class.java)?.toInstant(),
                    failedAt = row.get("failed_at", OffsetDateTime::class.java)?.toInstant(),
                    externalPublicationId = row.get("external_publication_id", String::class.java),
                    lastErrorCode = row.get("last_error_code", String::class.java),
                    lastErrorMessage = row.get("last_error_message", String::class.java),
                    createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                    updatedAt = row.get("updated_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .one()
            .awaitSingleOrNull() ?: return null

        val assetIds = databaseClient.sql(
            """
            SELECT asset_id
            FROM publication_asset_links
            WHERE publication_id = :publicationId
            ORDER BY position_index ASC
            """.trimIndent(),
        )
            .bind("publicationId", publication.id)
            .map { row, _ -> requireNotNull(row.get("asset_id", String::class.java)) }
            .all()
            .collectList()
            .awaitSingle()

        return publication.copy(assetIds = assetIds)
    }

    override suspend fun findInDateRange(
        workspaceId: String,
        from: Instant,
        to: Instant,
        statuses: Set<PublicationStatus>?,
        socialAccountIds: Set<String>?,
        hydrateAssets: Boolean,
    ): List<PublicationDraft> {
        val conditions = mutableListOf("workspace_id = :workspaceId", "scheduled_for >= :from", "scheduled_for < :to")
        val paramKeys = mutableMapOf(
            "workspaceId" to workspaceId,
            "from" to from,
            "to" to to,
        )

        statuses?.takeIf { it.isNotEmpty() }?.let { set ->
            val placeholders = set.mapIndexed { i, s ->
                val key = "status_$i"
                paramKeys[key] = s.name
                ":$key"
            }
            conditions.add("status IN (${placeholders.joinToString(", ")})")
        }

        socialAccountIds?.takeIf { it.isNotEmpty() }?.let { set ->
            val placeholders = set.mapIndexed { i, id ->
                val key = "account_$i"
                paramKeys[key] = id
                ":$key"
            }
            conditions.add("social_account_id IN (${placeholders.joinToString(", ")})")
        }

        val sql = """
            SELECT id, workspace_id, author_principal_id, provider, social_account_id, status, schedule_mode, priority,
                   title, body_text, public_url, blocked_at, blocked_reason, retry_count,
                   scheduled_for, next_slot_after, published_at, failed_at, external_publication_id,
                   last_error_code, last_error_message, created_at, updated_at
            FROM publications
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY scheduled_for ASC
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("workspaceId", paramKeys["workspaceId"]!!)
            .bind("from", paramKeys["from"]!!)
            .bind("to", paramKeys["to"]!!)

        statuses?.forEachIndexed { i, _ ->
            spec = spec.bind("status_$i", paramKeys["status_$i"]!!)
        }
        socialAccountIds?.forEachIndexed { i, _ ->
            spec = spec.bind("account_$i", paramKeys["account_$i"]!!)
        }

        val drafts = spec.map { row, _ -> row.toPublicationDraft() }
            .all()
            .collectList()
            .awaitSingle()

        return if (hydrateAssets) hydrateAssetIds(drafts) else drafts
    }

    override suspend fun countByDate(
        workspaceId: String,
        from: Instant,
        to: Instant,
        statuses: Set<PublicationStatus>?,
        timezone: String,
    ): List<DateCount> {
        val publications = findInDateRange(
            workspaceId = workspaceId,
            from = from,
            to = to,
            statuses = statuses,
            socialAccountIds = null,
            hydrateAssets = false,
        )

        val zoneId = java.time.ZoneId.of(timezone)

        return publications
            .mapNotNull { it.scheduledFor }
            .groupingBy { it.atZone(zoneId).toLocalDate() }
            .eachCount()
            .map { (date, count) -> DateCount(date = date, count = count) }
            .sortedBy { it.date }
    }

    private suspend fun hydrateAssetIds(drafts: List<PublicationDraft>): List<PublicationDraft> {
        val publicationIds = drafts.map { it.id }
        if (publicationIds.isEmpty()) return drafts

        val assetLinks = databaseClient.sql(
            """
            SELECT publication_id, asset_id FROM publication_asset_links
            WHERE publication_id IN (:ids)
            ORDER BY publication_id, position_index ASC
            """.trimIndent(),
        )
            .bind("ids", publicationIds)
            .map { row, _ ->
                requireNotNull(row.get(PUBLICATION_ID_COLUMN, String::class.java)) to
                    requireNotNull(row.get("asset_id", String::class.java))
            }
            .all()
            .collectList()
            .awaitSingle()

        val assetsByPublication: Map<String, List<String>> = assetLinks
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, value) -> value.filterNotNull() }

        return drafts.map { draft ->
            draft.copy(assetIds = assetsByPublication[draft.id].orEmpty())
        }
    }

    override suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant) {
        databaseClient.sql(
            """
            UPDATE publications
            SET status = :status,
                external_publication_id = :externalPublicationId,
                published_at = :publishedAt,
                failed_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("status", PublicationStatus.PUBLISHED.name)
            .bind("externalPublicationId", externalPublicationId)
            .bind("publishedAt", publishedAt)
            .bind("updatedAt", publishedAt)
            .bind("id", publicationId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun markFailed(
        publicationId: String,
        failedAt: Instant,
        reasonCode: String?,
        reasonMessage: String?,
    ) {
        databaseClient.sql(
            """
            UPDATE publications
            SET status = :status,
                failed_at = :failedAt,
                last_error_code = :reasonCode,
                last_error_message = :reasonMessage,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("status", PublicationStatus.FAILED.name)
            .bind("failedAt", failedAt)
            .bindNullable("reasonCode", reasonCode, String::class.java)
            .bindNullable("reasonMessage", reasonMessage, String::class.java)
            .bind("updatedAt", failedAt)
            .bind("id", publicationId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun markCancelled(publicationId: String, cancelledAt: Instant) {
        databaseClient.sql(
            """
            UPDATE publications
            SET status = :status,
                failed_at = :cancelledAt,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("status", PublicationStatus.CANCELLED.name)
            .bind("cancelledAt", cancelledAt)
            .bind("updatedAt", cancelledAt)
            .bind("id", publicationId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?) {
        databaseClient.sql(
            """
            UPDATE publications
            SET status = :status,
                blocked_at = :blockedAt,
                blocked_reason = :reason,
                updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("status", PublicationStatus.BLOCKED.name)
            .bind("blockedAt", blockedAt)
            .bindNullable("reason", reason, String::class.java)
            .bind("updatedAt", blockedAt)
            .bind("id", publicationId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun deleteUnpublished(workspaceId: String, publicationId: String): Boolean {
        val status = lockAndGetStatus(workspaceId, publicationId)
        if (status == null || status !in setOf("DRAFT", "QUEUED", "SCHEDULED")) {
            return false
        }
        return performCascadingDelete(workspaceId, publicationId)
    }

    private suspend fun lockAndGetStatus(workspaceId: String, publicationId: String): String? = databaseClient.sql(
        """
            SELECT status FROM publications
            WHERE workspace_id = :workspaceId
              AND id = :publicationId
            FOR UPDATE
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("publicationId", publicationId)
        .map { row, _ -> requireNotNull(row.get("status", String::class.java)) }
        .one()
        .awaitSingleOrNull()

    private suspend fun performCascadingDelete(workspaceId: String, publicationId: String): Boolean =
        transactionalOperator.transactional(
            mono {
                databaseClient.sql(
                    """
                DELETE FROM publication_jobs
                WHERE publication_id = :publicationId
                  AND workspace_id = :workspaceId
                  AND status IN (:pendingStatus, :retryWaitingStatus)
                    """.trimIndent(),
                )
                    .bind("publicationId", publicationId)
                    .bind("workspaceId", workspaceId)
                    .bind("pendingStatus", JobStatus.PENDING.name)
                    .bind("retryWaitingStatus", JobStatus.RETRY_WAITING.name)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

                databaseClient.sql(
                    """
                DELETE FROM publication_asset_links
                WHERE publication_id = :publicationId
                    """.trimIndent(),
                )
                    .bind("publicationId", publicationId)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

                val rows = databaseClient.sql(
                    """
                DELETE FROM publications
                WHERE workspace_id = :workspaceId
                  AND id = :publicationId
                  AND status IN ('DRAFT', 'QUEUED', 'SCHEDULED')
                    """.trimIndent(),
                )
                    .bind("workspaceId", workspaceId)
                    .bind("publicationId", publicationId)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

                rows > 0
            },
        ).awaitSingle()

    override suspend fun findBlockedForRecovery(maxRetries: Int): List<PublicationDraft> {
        val drafts = databaseClient.sql(
            """
            SELECT p.id, p.workspace_id, p.author_principal_id, p.provider, p.social_account_id, p.status, p.schedule_mode, p.priority,
                   p.title, p.body_text, p.public_url, p.blocked_at, p.blocked_reason, p.retry_count,
                   p.scheduled_for, p.next_slot_after, p.published_at, p.failed_at, p.external_publication_id,
                   p.last_error_code, p.last_error_message, p.created_at, p.updated_at
            FROM publications p
            JOIN social_accounts a
              ON a.id = p.social_account_id
             AND a.workspace_id = p.workspace_id
            WHERE p.status = :status
              AND p.retry_count < :maxRetries
              AND a.status = :activeStatus
            ORDER BY p.blocked_at ASC
            LIMIT 100
            FOR UPDATE OF p SKIP LOCKED
            """.trimIndent(),
        )
            .bind("status", PublicationStatus.BLOCKED.name)
            .bind("maxRetries", maxRetries)
            .bind("activeStatus", SocialConnectionStatus.ACTIVE.name)
            .map { row, _ -> row.toPublicationDraft() }
            .all()
            .collectList()
            .awaitSingle()

        // Hydrate asset IDs for each draft to prevent data loss on recovery
        val publicationIds = drafts.map { it.id }
        if (publicationIds.isNotEmpty()) {
            val assetLinks = databaseClient.sql(
                """
                SELECT publication_id, asset_id FROM publication_asset_links
                WHERE publication_id IN (:ids)
                ORDER BY publication_id, position_index ASC
                """.trimIndent(),
            )
                .bind("ids", publicationIds)
                .map { row, _ ->
                    requireNotNull(row.get(PUBLICATION_ID_COLUMN, String::class.java)) to
                        requireNotNull(row.get("asset_id", String::class.java))
                }
                .all()
                .collectList()
                .awaitSingle()

            val assetsByPublication: Map<String, List<String>> = assetLinks
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.filterNotNull() }
            return drafts.map { draft ->
                draft.copy(assetIds = assetsByPublication[draft.id].orEmpty())
            }
        }
        return drafts
    }

    private suspend fun insertOrUpdate(draft: PublicationDraft) {
        val now = Instant.now()
        val createdAt = draft.createdAt ?: now

        val updatedRows = databaseClient.sql(
            """
            UPDATE publications
            SET $PUBLICATION_UPDATE_COLUMNS
            WHERE id = :id AND workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bindPublicationUpdateParams(draft, now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        if (updatedRows > 0) return

        val existingWorkspaceId = databaseClient.sql(
            """
            SELECT workspace_id
            FROM publications
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", draft.id)
            .map { row, _ -> requireNotNull(row.get("workspace_id", String::class.java)) }
            .one()
            .awaitSingleOrNull()

        check(existingWorkspaceId == null || existingWorkspaceId == draft.workspaceId) {
            "Publication ${draft.id} cannot be written from workspace " +
                "${draft.workspaceId}; it belongs to a different current " +
                "workspace scope"
        }

        databaseClient.sql(
            """
            INSERT INTO publications (
                $PUBLICATION_INSERT_COLUMNS
            ) VALUES (
                $PUBLICATION_INSERT_VALUES
            )
            """.trimIndent(),
        )
            .bindPublicationInsertParams(draft, createdAt, now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindPublicationUpdateParams(
        draft: PublicationDraft,
        updatedAt: Instant,
    ): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
        bindPublicationUpdateWriteParams(draft, updatedAt)
            .bind("id", draft.id)

    private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindPublicationInsertParams(
        draft: PublicationDraft,
        createdAt: Instant,
        updatedAt: Instant,
    ): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec = bind("id", draft.id)
        .bindPublicationWriteParams(draft, createdAt, updatedAt)

    private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindPublicationUpdateWriteParams(
        draft: PublicationDraft,
        updatedAt: Instant,
    ): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec = bind("workspaceId", draft.workspaceId)
        .bind("authorPrincipalId", draft.authorPrincipalId)
        .bind("provider", draft.provider.name)
        .bind("socialAccountId", draft.socialAccountId)
        .bind("status", draft.status.name)
        .bind("scheduleMode", draft.scheduleMode.name)
        .bind("priority", draft.priority)
        .bindNullable("title", draft.title, String::class.java)
        .bindNullable("bodyText", draft.bodyText, String::class.java)
        .bindNullable("publicUrl", draft.publicUrl, String::class.java)
        .bindNullable("blockedAt", draft.blockedAt, Instant::class.java)
        .bindNullable("blockedReason", draft.blockedReason, String::class.java)
        .bind("retryCount", draft.retryCount)
        .bindNullable("scheduledFor", draft.scheduledFor, Instant::class.java)
        .bindNullable("nextSlotAfter", draft.nextSlotAfter, Instant::class.java)
        .bindNullable("publishedAt", draft.publishedAt, Instant::class.java)
        .bindNullable("failedAt", draft.failedAt, Instant::class.java)
        .bindNullable("externalPublicationId", draft.externalPublicationId, String::class.java)
        .bindNullable("lastErrorCode", draft.lastErrorCode, String::class.java)
        .bindNullable("lastErrorMessage", draft.lastErrorMessage, String::class.java)
        .bind("updatedAt", updatedAt)

    private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindPublicationWriteParams(
        draft: PublicationDraft,
        createdAt: Instant,
        updatedAt: Instant,
    ): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
        bindPublicationUpdateWriteParams(draft, updatedAt)
            .bind("createdAt", createdAt)

    private suspend fun replaceAssetLinks(draft: PublicationDraft) {
        databaseClient.sql(
            """
            DELETE FROM publication_asset_links
            WHERE publication_id = :publicationId
            """.trimIndent(),
        )
            .bind("publicationId", draft.id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        draft.assetIds.forEachIndexed { index, assetId ->
            databaseClient.sql(
                """
                INSERT INTO publication_asset_links
                (publication_id, asset_id, position_index)
                VALUES (:publicationId, :assetId, :positionIndex)
                """.trimIndent(),
            )
                .bind("publicationId", draft.id)
                .bind("assetId", assetId)
                .bind("positionIndex", index)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }
}

@Repository
class R2dbcPublicationAssetRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : PublicationAssetRepository {
    override suspend fun findByWorkspaceAndIds(
        workspaceId: String,
        assetIds: Collection<String>,
    ): List<PublicationAsset> {
        if (assetIds.isEmpty()) return emptyList()
        return databaseClient.sql(
            """
            SELECT id, workspace_id, source_type, media_type, storage_key, external_url, original_filename, file_size_bytes, status, created_by_principal_id, created_at, provider_asset_ref
            FROM publication_assets
            WHERE workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .map { row, _ ->
                PublicationAsset(
                    id = requireNotNull(row.get("id", String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    sourceType = AssetSourceType.valueOf(requireNotNull(row.get("source_type", String::class.java))),
                    mediaType = requireNotNull(row.get("media_type", String::class.java)),
                    storageKey = row.get("storage_key", String::class.java),
                    externalUrl = row.get("external_url", String::class.java),
                    originalFilename = row.get("original_filename", String::class.java),
                    fileSizeBytes = row.get("file_size_bytes", Long::class.javaObjectType),
                    status = PublicationAssetStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                    providerAssetRef = row.get("provider_asset_ref", String::class.java)?.let { json ->
                        runCatching {
                            objectMapper.readValue(
                                json,
                                com.profiletailors.smp.publishing.domain.ProviderAssetRef::class.java,
                            )
                        }.getOrNull()
                    },
                    createdByPrincipalId = requireNotNull(row.get("created_by_principal_id", String::class.java)),
                    createdAt = row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
            .filter { it.id in assetIds }
    }

    override suspend fun create(asset: PublicationAsset): PublicationAsset {
        val providerAssetRefJson = asset.providerAssetRef?.let {
            runCatching { objectMapper.writeValueAsString(it) }.getOrNull()
        }
        databaseClient.sql(
            """
            INSERT INTO publication_assets (
                id, workspace_id, source_type, media_type, storage_key, external_url,
                original_filename, file_size_bytes, status, provider_asset_ref, created_by_principal_id, created_at
            ) VALUES (
                :id, :workspaceId, :sourceType, :mediaType, :storageKey, :externalUrl,
                :originalFilename, :fileSizeBytes, :status, :providerAssetRef, :createdByPrincipalId, :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", asset.id)
            .bind("workspaceId", asset.workspaceId)
            .bind("sourceType", asset.sourceType.name)
            .bind("mediaType", asset.mediaType)
            .bindNullable("storageKey", asset.storageKey, String::class.java)
            .bindNullable("externalUrl", asset.externalUrl, String::class.java)
            .bindNullable("originalFilename", asset.originalFilename, String::class.java)
            .let { spec ->
                val fileSize = asset.fileSizeBytes
                if (fileSize != null) {
                    spec.bind("fileSizeBytes", fileSize)
                } else {
                    spec.bindNull("fileSizeBytes", Long::class.javaObjectType)
                }
            }
            .bind("status", asset.status.name)
            .bindNullable("providerAssetRef", providerAssetRefJson, String::class.java)
            .bind("createdByPrincipalId", asset.createdByPrincipalId)
            .bindNullable("createdAt", asset.createdAt ?: Instant.now(), Instant::class.java)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return asset
    }

    override suspend fun updateStatus(assetId: String, status: PublicationAssetStatus) {
        databaseClient.sql(
            """
            UPDATE publication_assets
            SET status = :status
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("status", status.name)
            .bind("id", assetId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun updateProviderAssetRef(assetId: String, providerAssetRef: ProviderAssetRef) {
        val providerAssetRefJson = try {
            objectMapper.writeValueAsString(providerAssetRef)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Failed to serialize provider asset ref", e)
        }
        databaseClient.sql(
            """
            UPDATE publication_assets
            SET status = :status, provider_asset_ref = :providerAssetRef
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("status", PublicationAssetStatus.READY.name)
            .bind("providerAssetRef", providerAssetRefJson)
            .bind("id", assetId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}

@Repository
class R2dbcPublicationJobRepository(private val databaseClient: DatabaseClient) : PublicationJobRepository {
    override suspend fun enqueue(job: PublicationJob) {
        insertJob(job)
    }

    override suspend fun replaceForPublication(job: PublicationJob) {
        databaseClient.sql("DELETE FROM delivery_attempts WHERE publication_id = :publicationId")
            .bind("publicationId", job.publicationId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            "DELETE FROM publication_jobs WHERE publication_id = :publicationId AND workspace_id = :workspaceId",
        )
            .bind("publicationId", job.publicationId)
            .bind("workspaceId", job.workspaceId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        insertJob(job)
    }

    override suspend fun claimNextDue(now: Instant, workerId: String, claimLease: Duration): PublicationJobClaim? {
        require(!claimLease.isNegative && !claimLease.isZero) { "Claim lease must be positive." }
        val leaseExpiresAt = now.plus(claimLease)
        val row = claimAndMapRow(now, workerId, leaseExpiresAt) ?: return null
        val recoveredOperationKey = findRecoverableOperationKey(row.jobId)
        return row.copy(operationKey = recoveredOperationKey ?: row.operationKey)
    }

    private suspend fun claimAndMapRow(now: Instant, workerId: String, leaseExpiresAt: Instant) = databaseClient.sql(
        """
            WITH next_job AS (
                SELECT job.id,
                       job.publication_id,
                       job.workspace_id,
                       job.attempt_count,
                       job.claim_version,
                       EXISTS (
                           SELECT 1
                           FROM delivery_attempts attempt
                           WHERE attempt.publication_job_id = job.id
                             AND attempt.outcome IN ('IN_PROGRESS', 'SUCCEEDED')
                       ) AS has_recoverable_attempt
                FROM publication_jobs job
                WHERE job.status IN ('PENDING', 'RETRY_WAITING')
                  AND job.due_at <= :now
                ORDER BY job.priority_rank DESC, job.due_at ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE publication_jobs AS job
            SET status = :status,
                claimed_by_worker = :workerId,
                claimed_at = :claimedAt,
                lease_expires_at = :leaseExpiresAt,
                attempt_count = CASE
                    WHEN next_job.has_recoverable_attempt THEN job.attempt_count
                    ELSE job.attempt_count + 1
                END,
                claim_version = job.claim_version + 1
            FROM next_job
            WHERE job.id = next_job.id
            RETURNING job.id,
                      job.publication_id,
                      job.workspace_id,
                      job.attempt_count,
                      job.claim_version
        """.trimIndent(),
    )
        .bind("now", now)
        .bind("status", JobStatus.CLAIMED.name)
        .bind("workerId", workerId)
        .bind("claimedAt", now)
        .bind("leaseExpiresAt", leaseExpiresAt)
        .map { resultRow, _ ->
            PublicationJobClaim(
                jobId = requireNotNull(resultRow.get("id", String::class.java)),
                publicationId = requireNotNull(resultRow.get(PUBLICATION_ID_COLUMN, String::class.java)),
                workspaceId = requireNotNull(resultRow.get("workspace_id", String::class.java)),
                attemptNumber = requireNotNull(resultRow.get("attempt_count", Int::class.javaObjectType)),
                claimedAt = now,
                leaseExpiresAt = leaseExpiresAt,
                claimVersion = requireNotNull(resultRow.get("claim_version", Long::class.javaObjectType)),
            )
        }
        .one()
        .awaitSingleOrNull()

    private suspend fun findRecoverableOperationKey(publicationJobId: String): String? = databaseClient.sql(
        """
        SELECT operation_key
        FROM delivery_attempts
        WHERE publication_job_id = :publicationJobId
          AND outcome IN ('IN_PROGRESS', 'SUCCEEDED')
        ORDER BY attempt_number DESC
        LIMIT 1
        """.trimIndent(),
    )
        .bind("publicationJobId", publicationJobId)
        .map { resultRow, _ -> requireNotNull(resultRow.get("operation_key", String::class.java)) }
        .one()
        .awaitSingleOrNull()

    @Suppress("StringLiteralDuplication")
    override suspend fun rescheduleRetry(
        jobId: String,
        claimVersion: Long,
        nextAttemptAt: Instant,
        attemptNumber: Int,
    ): Boolean = databaseClient.sql(
        """
            UPDATE publication_jobs
            SET status = :status,
                due_at = :nextAttemptAt,
                attempt_count = :attemptCount,
                 claimed_by_worker = NULL,
                 claimed_at = NULL,
                 lease_expires_at = NULL,
                 claim_version = claim_version + 1

            WHERE id = :id AND claim_version = :claimVersion
        """.trimIndent(),
    )
        .bind("status", JobStatus.RETRY_WAITING.name)
        .bind("nextAttemptAt", nextAttemptAt)
        .bind("attemptCount", attemptNumber)
        .bind("id", jobId)
        .bind("claimVersion", claimVersion)
        .fetch()
        .rowsUpdated()
        .awaitSingle() > 0

    override suspend fun complete(jobId: String, claimVersion: Long, completedAt: Instant): Boolean =
        databaseClient.sql(
            """
            UPDATE publication_jobs
            SET status = :status, completed_at = :completedAt, claim_version = claim_version + 1
            WHERE id = :id AND claim_version = :claimVersion
            """.trimIndent(),
        )
            .bind("status", JobStatus.COMPLETED.name)
            .bind("completedAt", completedAt)
            .bind("id", jobId)
            .bind("claimVersion", claimVersion)
            .fetch()
            .rowsUpdated()
            .awaitSingle() > 0

    override suspend fun fail(jobId: String, claimVersion: Long, failedAt: Instant): Boolean = databaseClient.sql(
        """
            UPDATE publication_jobs
            SET status = :status, failed_at = :failedAt, claim_version = claim_version + 1
            WHERE id = :id AND claim_version = :claimVersion
        """.trimIndent(),
    )
        .bind("status", JobStatus.FAILED.name)
        .bind("failedAt", failedAt)
        .bind("id", jobId)
        .bind("claimVersion", claimVersion)
        .fetch()
        .rowsUpdated()
        .awaitSingle() > 0

    override suspend fun cancel(jobId: String, cancelledAt: Instant) {
        databaseClient.sql(
            """
            UPDATE publication_jobs
            SET status = :status, cancelled_at = :cancelledAt
            WHERE publication_id = :publicationId
            """.trimIndent(),
        )
            .bind("status", JobStatus.CANCELLED.name)
            .bind("cancelledAt", cancelledAt)
            .bind("publicationId", jobId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun findStaleClaims(now: Instant, leaseStaleThreshold: Duration): List<StaleJob> {
        require(!leaseStaleThreshold.isNegative && !leaseStaleThreshold.isZero) {
            "Lease stale threshold must be positive."
        }
        val thresholdAt = now.minus(leaseStaleThreshold)
        return databaseClient.sql(
            """
            SELECT id, publication_id, workspace_id, claimed_by_worker,
                   claimed_at, lease_expires_at, attempt_count
            FROM publication_jobs
            WHERE status = 'CLAIMED'
              AND lease_expires_at < :thresholdAt
            ORDER BY lease_expires_at ASC
            LIMIT 100
            """.trimIndent(),
        )
            .bind("thresholdAt", thresholdAt)
            .map { row, _ ->
                StaleJob(
                    jobId = requireNotNull(row.get("id", String::class.java)),
                    publicationId = requireNotNull(row.get(PUBLICATION_ID_COLUMN, String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    claimedByWorker = requireNotNull(row.get("claimed_by_worker", String::class.java)),
                    claimedAt = requireNotNull(row.get("claimed_at", OffsetDateTime::class.java)).toInstant(),
                    leaseExpiresAt = requireNotNull(
                        row.get("lease_expires_at", OffsetDateTime::class.java),
                    ).toInstant(),
                    attemptNumber = requireNotNull(row.get("attempt_count", Int::class.javaObjectType)),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun releaseExpiredClaims(now: Instant, leaseStaleThreshold: Duration): Int {
        require(!leaseStaleThreshold.isNegative && !leaseStaleThreshold.isZero) {
            "Lease stale threshold must be positive."
        }
        val thresholdAt = now.minus(leaseStaleThreshold)
        return databaseClient.sql(
            """
            UPDATE publication_jobs
            SET status = 'PENDING',
                claimed_by_worker = NULL,
                claimed_at = NULL,
                lease_expires_at = NULL
            WHERE status = 'CLAIMED'
              AND lease_expires_at < :thresholdAt
            """.trimIndent(),
        )
            .bind("thresholdAt", thresholdAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
            .toInt()
    }

    private suspend fun insertJob(job: PublicationJob) {
        databaseClient.sql(
            """
            INSERT INTO publication_jobs (
                id, publication_id, workspace_id, status, due_at, priority_rank, attempt_count, max_attempts,
                claimed_by_worker, claimed_at, lease_expires_at, completed_at, failed_at, cancelled_at, created_at
            ) VALUES (
                :id, :publicationId, :workspaceId, :status, :dueAt, :priorityRank, :attemptCount, :maxAttempts,
                :claimedByWorker, :claimedAt, :leaseExpiresAt, :completedAt, :failedAt, :cancelledAt, :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", job.id)
            .bind("publicationId", job.publicationId)
            .bind("workspaceId", job.workspaceId)
            .bind("status", job.status.name)
            .bind("dueAt", job.dueAt)
            .bind("priorityRank", job.priorityRank)
            .bind("attemptCount", job.attemptCount)
            .bind("maxAttempts", job.maxAttempts)
            .bindNullable("claimedByWorker", job.claimedByWorker, String::class.java)
            .bindNullable("claimedAt", job.claimedAt, Instant::class.java)
            .bindNullable("leaseExpiresAt", job.leaseExpiresAt, Instant::class.java)
            .bindNullable("completedAt", job.completedAt, Instant::class.java)
            .bindNullable("failedAt", job.failedAt, Instant::class.java)
            .bindNullable("cancelledAt", job.cancelledAt, Instant::class.java)
            .bindNullable("createdAt", job.createdAt ?: Instant.now(), Instant::class.java)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}

@Repository
class R2dbcDeliveryAttemptRepository(private val databaseClient: DatabaseClient) : DeliveryAttemptRepository {
    override suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt {
        databaseClient.sql(
            """
            INSERT INTO delivery_attempts (
                id, publication_id, publication_job_id, attempt_number, outcome, retryable,
                provider_message, provider_error_code, external_publication_id, attempted_at, created_at,
                operation_key, claim_version, phase
            ) VALUES (
                :id, :publicationId, :publicationJobId, :attemptNumber, :outcome, :retryable,
                :providerMessage, :providerErrorCode, :externalPublicationId, :attemptedAt, :createdAt,
                :operationKey, :claimVersion, :phase
            )
            """.trimIndent(),
        )
            .bind("id", attempt.id)
            .bind("publicationId", attempt.publicationId)
            .bind("publicationJobId", attempt.publicationJobId)
            .bind("attemptNumber", attempt.attemptNumber)
            .bind("outcome", attempt.outcome.name)
            .bind("retryable", attempt.retryable)
            .bindNullable("providerMessage", attempt.providerMessage, String::class.java)
            .bindNullable("providerErrorCode", attempt.providerErrorCode, String::class.java)
            .bindNullable("externalPublicationId", attempt.externalPublicationId, String::class.java)
            .bind("attemptedAt", attempt.attemptedAt)
            .bindNullable("createdAt", attempt.createdAt ?: attempt.attemptedAt, Instant::class.java)
            .bind("operationKey", attempt.operationKey ?: "${attempt.publicationJobId}:${attempt.attemptNumber}")
            .bind("claimVersion", attempt.claimVersion ?: 0L)
            .bind("phase", attempt.phase?.name ?: DeliveryAttemptPhase.FINALIZATION.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return attempt
    }

    override suspend fun findByOperationKey(operationKey: String): DeliveryAttempt? = databaseClient.sql(
        """
            SELECT id, publication_id, publication_job_id, attempt_number, outcome, retryable,
                   provider_message, provider_error_code, external_publication_id, attempted_at, created_at,
                   operation_key, claim_version, phase
            FROM delivery_attempts
            WHERE operation_key = :operationKey
            ORDER BY created_at DESC
            LIMIT 1
        """.trimIndent(),
    )
        .bind("operationKey", operationKey)
        .map { row, _ -> row.toDeliveryAttempt() }
        .one()
        .awaitSingleOrNull()

    override suspend fun update(attempt: DeliveryAttempt): Boolean = databaseClient.sql(
        """
            UPDATE delivery_attempts
            SET outcome = :outcome,
                retryable = :retryable,
                provider_message = :providerMessage,
                provider_error_code = :providerErrorCode,
                external_publication_id = :externalPublicationId,
                attempted_at = :attemptedAt,
                phase = :phase,
                claim_version = claim_version + 1
            WHERE id = :id AND claim_version = :claimVersion
        """.trimIndent(),
    )
        .bind("outcome", attempt.outcome.name)
        .bind("retryable", attempt.retryable)
        .bindNullable("providerMessage", attempt.providerMessage, String::class.java)
        .bindNullable("providerErrorCode", attempt.providerErrorCode, String::class.java)
        .bindNullable("externalPublicationId", attempt.externalPublicationId, String::class.java)
        .bind("attemptedAt", attempt.attemptedAt)
        .bindNullable("phase", attempt.phase?.name, String::class.java)
        .bind("id", attempt.id)
        .bindNullable("claimVersion", attempt.claimVersion, Long::class.java)
        .fetch()
        .rowsUpdated()
        .awaitSingle() > 0

    private fun Readable.toDeliveryAttempt(): DeliveryAttempt = DeliveryAttempt(
        id = requireNotNull(get("id", String::class.java)),
        publicationId = requireNotNull(get("publication_id", String::class.java)),
        publicationJobId = requireNotNull(get("publication_job_id", String::class.java)),
        attemptNumber = requireNotNull(get("attempt_number", Int::class.java)),
        outcome = DeliveryAttemptOutcome.valueOf(requireNotNull(get("outcome", String::class.java))),
        retryable = requireNotNull(get("retryable", Boolean::class.java)),
        providerMessage = get("provider_message", String::class.java),
        providerErrorCode = get("provider_error_code", String::class.java),
        externalPublicationId = get("external_publication_id", String::class.java),
        attemptedAt = requireNotNull(get("attempted_at", Instant::class.java)),
        createdAt = get("created_at", Instant::class.java),
        operationKey = get("operation_key", String::class.java),
        claimVersion = get("claim_version", Long::class.java) ?: 0L,
        phase =
        get("phase", String::class.java)?.let { DeliveryAttemptPhase.valueOf(it) }
            ?: DeliveryAttemptPhase.FINALIZATION,
    )
}

internal fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: String?,
    type: Class<String>,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
    value?.let { bind(name, it) } ?: bindNull(name, type)

internal fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: java.time.Instant?,
    type: Class<java.time.Instant>,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
    value?.let { bind(name, it) } ?: bindNull(name, type)

internal fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: Long?,
    type: Class<Long>,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
    value?.let { bind(name, it) } ?: bindNull(name, type)

internal fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: Int?,
    type: Class<Int>,
): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
    value?.let { bind(name, it) } ?: bindNull(name, type)

private fun Readable.toPublicationDraft(): PublicationDraft = PublicationDraft(
    id = requireNotNull(get("id", String::class.java)),
    workspaceId = requireNotNull(get("workspace_id", String::class.java)),
    authorPrincipalId = requireNotNull(get("author_principal_id", String::class.java)),
    provider = SocialProvider.valueOf(requireNotNull(get("provider", String::class.java))),
    socialAccountId = requireNotNull(get("social_account_id", String::class.java)),
    status = PublicationStatus.valueOf(requireNotNull(get("status", String::class.java))),
    scheduleMode = ScheduleMode.valueOf(requireNotNull(get("schedule_mode", String::class.java))),
    priority = requireNotNull(get("priority", Boolean::class.javaObjectType)),
    title = get("title", String::class.java),
    bodyText = get("body_text", String::class.java),
    publicUrl = get("public_url", String::class.java),
    blockedAt = get("blocked_at", OffsetDateTime::class.java)?.toInstant(),
    blockedReason = get("blocked_reason", String::class.java),
    retryCount = get("retry_count", Int::class.javaObjectType) ?: 0,
    assetIds = emptyList(),
    scheduledFor = get("scheduled_for", OffsetDateTime::class.java)?.toInstant(),
    nextSlotAfter = get("next_slot_after", OffsetDateTime::class.java)?.toInstant(),
    publishedAt = get("published_at", OffsetDateTime::class.java)?.toInstant(),
    failedAt = get("failed_at", OffsetDateTime::class.java)?.toInstant(),
    externalPublicationId = get("external_publication_id", String::class.java),
    lastErrorCode = get("last_error_code", String::class.java),
    lastErrorMessage = get("last_error_message", String::class.java),
    createdAt = get("created_at", OffsetDateTime::class.java)?.toInstant(),
    updatedAt = get("updated_at", OffsetDateTime::class.java)?.toInstant(),
)
