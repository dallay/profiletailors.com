package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcWaitlistInvitationRepository(private val databaseClient: DatabaseClient) : WaitlistInvitationRepository {

    override suspend fun findById(id: WaitlistInvitationId): WaitlistInvitation? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id.value)
        .map { row, _ -> row.toInvitation() }
        .one()
        .awaitSingleOrNull()

    override suspend fun findActiveByWaitlistEntryId(waitlistEntryId: String): WaitlistInvitation? =
        databaseClient.sql(SELECT_ACTIVE_BY_ENTRY)
            .bind("waitlistEntryId", waitlistEntryId)
            .bind("status", WaitlistInvitationStatus.ACTIVE.name)
            .map { row, _ -> row.toInvitation() }
            .one()
            .awaitSingleOrNull()

    override suspend fun findAllByWaitlistEntryId(waitlistEntryId: String): List<WaitlistInvitation> =
        databaseClient.sql(SELECT_ALL_BY_ENTRY)
            .bind("waitlistEntryId", waitlistEntryId)
            .map { row, _ -> row.toInvitation() }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun findByTokenHash(tokenHash: String): WaitlistInvitation? =
        databaseClient.sql(SELECT_BY_TOKEN_HASH)
            .bind("tokenHash", tokenHash)
            .map { row, _ -> row.toInvitation() }
            .one()
            .awaitSingleOrNull()

    override suspend fun save(invitation: WaitlistInvitation): WaitlistInvitation {
        databaseClient.sql(INSERT)
            .bindInvitation(invitation)
            .then()
            .awaitSingleOrNull()
        return requireNotNull(findById(invitation.id))
    }

    override suspend fun update(invitation: WaitlistInvitation): WaitlistInvitation {
        val acceptedAt = invitation.acceptedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        val revokedAt = invitation.revokedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        val lastDeliveryAttemptAt = invitation.lastDeliveryAttemptAt?.let {
            OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }
        val rowsUpdated = databaseClient.sql(UPDATE)
            .bind("status", invitation.status.name)
            .bindNullable("acceptedAt", acceptedAt, OffsetDateTime::class.java)
            .bindNullable("revokedAt", revokedAt, OffsetDateTime::class.java)
            .bindNullable("revokedBy", invitation.revokedBy, UUID::class.java)
            .bind("deliveryStatus", invitation.deliveryStatus.name)
            .bindNullable("lastDeliveryAttemptAt", lastDeliveryAttemptAt, OffsetDateTime::class.java)
            .bind("deliveryAttemptCount", invitation.deliveryAttemptCount)
            .bind("version", invitation.version + 1)
            .bind("id", invitation.id.value)
            .bind("currentVersion", invitation.version)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        if (rowsUpdated == 0L) {
            throw InvitationVersionConflictException(invitation.id.value.toString())
        }
        return requireNotNull(findById(invitation.id))
    }

    override suspend fun countResendsSince(waitlistEntryId: String, sinceEpochMillis: Long): Int =
        databaseClient.sql(COUNT_RESENDS_SINCE)
            .bind("waitlistEntryId", waitlistEntryId)
            .bind("since", OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(sinceEpochMillis), ZoneOffset.UTC))
            .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one()
            .awaitSingle()
            .toInt()

    private fun DatabaseClient.GenericExecuteSpec.bindInvitation(
        i: WaitlistInvitation,
    ): DatabaseClient.GenericExecuteSpec = bind("id", i.id.value)
        .bind("waitlistEntryId", i.waitlistEntryId)
        .bind("tokenHash", i.tokenHash)
        .bind("status", i.status.name)
        .bind("issuedAt", OffsetDateTime.ofInstant(i.issuedAt, ZoneOffset.UTC))
        .bind("expiresAt", OffsetDateTime.ofInstant(i.expiresAt, ZoneOffset.UTC))
        .bindNullable(
            "acceptedAt",
            i.acceptedAt?.let {
                OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
            },
            OffsetDateTime::class.java,
        )
        .bindNullable(
            "revokedAt",
            i.revokedAt?.let {
                OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
            },
            OffsetDateTime::class.java,
        )
        .bindNullable("revokedBy", i.revokedBy, UUID::class.java)
        .bind("createdBy", i.createdBy)
        .bind("deliveryStatus", i.deliveryStatus.name)
        .bindNullable(
            "lastDeliveryAttemptAt",
            i.lastDeliveryAttemptAt?.let {
                OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
            },
            OffsetDateTime::class.java,
        )
        .bind("deliveryAttemptCount", i.deliveryAttemptCount)
        .bind("version", i.version)

    private fun Readable.toInvitation() = WaitlistInvitation(
        id = WaitlistInvitationId(requireNotNull(get("id", UUID::class.java))),
        waitlistEntryId = requireNotNull(get("waitlist_entry_id", String::class.java)),
        tokenHash = requireNotNull(get("token_hash", String::class.java)),
        status = WaitlistInvitationStatus.valueOf(requireNotNull(get("status", String::class.java))),
        issuedAt = requireNotNull(get("issued_at", OffsetDateTime::class.java)).toInstant(),
        expiresAt = requireNotNull(get("expires_at", OffsetDateTime::class.java)).toInstant(),
        acceptedAt = get("accepted_at", OffsetDateTime::class.java)?.toInstant(),
        revokedAt = get("revoked_at", OffsetDateTime::class.java)?.toInstant(),
        revokedBy = get("revoked_by", UUID::class.java),
        createdBy = requireNotNull(get("created_by", UUID::class.java)),
        deliveryStatus = InvitationDeliveryStatus.valueOf(requireNotNull(get("delivery_status", String::class.java))),
        lastDeliveryAttemptAt = get("last_delivery_attempt_at", OffsetDateTime::class.java)?.toInstant(),
        deliveryAttemptCount = requireNotNull(get("delivery_attempt_count", Integer::class.java)).toInt(),
        version = requireNotNull(get("version", Long::class.java)),
    )

    companion object {
        private const val COLS = """
            id, waitlist_entry_id, token_hash, status, issued_at, expires_at, accepted_at,
            revoked_at, revoked_by, created_by, delivery_status, last_delivery_attempt_at,
            delivery_attempt_count, version
        """
        private const val SELECT_BY_ID = "SELECT $COLS FROM waitlist_invitations WHERE id = :id"
        private const val SELECT_ACTIVE_BY_ENTRY = """
            SELECT $COLS FROM waitlist_invitations
            WHERE waitlist_entry_id = :waitlistEntryId AND status = :status
        """
        private const val SELECT_ALL_BY_ENTRY = """
            SELECT $COLS FROM waitlist_invitations
            WHERE waitlist_entry_id = :waitlistEntryId ORDER BY issued_at DESC
        """
        private const val SELECT_BY_TOKEN_HASH = "SELECT $COLS FROM waitlist_invitations WHERE token_hash = :tokenHash"
        private const val INSERT = """
            INSERT INTO waitlist_invitations
              (id, waitlist_entry_id, token_hash, status, issued_at, expires_at, accepted_at,
               revoked_at, revoked_by, created_by, delivery_status, last_delivery_attempt_at,
               delivery_attempt_count, version)
            VALUES
              (:id, :waitlistEntryId, :tokenHash, :status, :issuedAt, :expiresAt, :acceptedAt,
               :revokedAt, :revokedBy, :createdBy, :deliveryStatus, :lastDeliveryAttemptAt,
               :deliveryAttemptCount, :version)
        """
        private const val UPDATE = """
            UPDATE waitlist_invitations
            SET status = :status, accepted_at = :acceptedAt, revoked_at = :revokedAt,
                revoked_by = :revokedBy, delivery_status = :deliveryStatus,
                last_delivery_attempt_at = :lastDeliveryAttemptAt,
                delivery_attempt_count = :deliveryAttemptCount, version = :version
            WHERE id = :id AND version = :currentVersion
        """
        private const val COUNT_RESENDS_SINCE = """
            SELECT COUNT(*) FROM waitlist_invitations
            WHERE waitlist_entry_id = :waitlistEntryId
              AND status IN ('ACTIVE', 'SUPERSEDED', 'ACCEPTED', 'REVOKED')
              AND issued_at >= :since
        """
    }
}

private fun <T> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, type)
