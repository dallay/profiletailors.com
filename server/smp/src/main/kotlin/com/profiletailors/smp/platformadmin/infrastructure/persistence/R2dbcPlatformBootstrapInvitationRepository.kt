package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationStatus
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcPlatformBootstrapInvitationRepository(private val databaseClient: DatabaseClient) :
    BootstrapInvitationRepository {

    override suspend fun lock() {
        databaseClient.sql("SELECT pg_advisory_xact_lock(:lockId)")
            .bind("lockId", BOOTSTRAP_ADVISORY_LOCK)
            .map { _, _ -> true }
            .one()
            .awaitSingle()
    }

    override suspend fun hasActiveOwner(): Boolean = databaseClient.sql(
        "SELECT EXISTS (SELECT 1 FROM platform_role_assignments WHERE role = 'PLATFORM_OWNER' AND revoked_at IS NULL)",
    ).map { row, _ -> requireNotNull(row.get(0, Boolean::class.javaObjectType)) }
        .one()
        .awaitSingle()

    override suspend fun findCurrent(): BootstrapInvitation? = databaseClient.sql(SELECT_CURRENT)
        .map { row, _ -> row.toInvitation() }
        .one()
        .awaitSingleOrNull()

    override suspend fun replace(invitation: BootstrapInvitation) {
        databaseClient.sql(REPLACE)
            .bind("email", invitation.email)
            .bind("tokenHash", invitation.tokenHash)
            .bind("expiresAt", invitation.expiresAt.toDatabaseTime())
            .bind("createdAt", invitation.createdAt.toDatabaseTime())
            .then()
            .awaitSingleOrNull()
    }

    override suspend fun findByTokenHash(tokenHash: String): BootstrapInvitation? = databaseClient.sql(SELECT_BY_TOKEN)
        .bind("tokenHash", tokenHash)
        .map { row, _ -> row.toInvitation() }
        .one()
        .awaitSingleOrNull()

    override suspend fun claim(tokenHash: String, principalId: UUID, now: Instant): Boolean = databaseClient.sql(CLAIM)
        .bind("tokenHash", tokenHash)
        .bind("principalId", principalId)
        .bind("now", now.toDatabaseTime())
        .fetch()
        .rowsUpdated()
        .awaitSingle() == 1L

    override suspend fun findClaimedByPrincipalId(principalId: UUID): BootstrapInvitation? =
        databaseClient.sql(SELECT_CLAIMED)
            .bind("principalId", principalId)
            .map { row, _ -> row.toInvitation() }
            .one()
            .awaitSingleOrNull()

    override suspend fun complete(principalId: UUID, now: Instant): Boolean = databaseClient.sql(COMPLETE)
        .bind("principalId", principalId)
        .bind("now", now.toDatabaseTime())
        .fetch()
        .rowsUpdated()
        .awaitSingle() == 1L

    private fun Instant.toDatabaseTime(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun Readable.toInvitation() = BootstrapInvitation(
        email = requireNotNull(get("email_normalized", String::class.java)),
        tokenHash = requireNotNull(get("token_hash", String::class.java)),
        expiresAt = requireNotNull(get("expires_at", OffsetDateTime::class.java)).toInstant(),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        status = BootstrapInvitationStatus.valueOf(requireNotNull(get("status", String::class.java))),
        claimedPrincipalId = get("claimed_principal_id", UUID::class.java),
        claimedAt = get("claimed_at", OffsetDateTime::class.java)?.toInstant(),
        completedAt = get("completed_at", OffsetDateTime::class.java)?.toInstant(),
    )

    companion object {
        private const val BOOTSTRAP_ADVISORY_LOCK = 5788059102132218708L
        private const val INVITATION_COLUMNS = """
            email_normalized, token_hash, expires_at, created_at, status,
            claimed_principal_id, claimed_at, completed_at
        """
        private const val SELECT_CURRENT =
            "SELECT $INVITATION_COLUMNS " +
                "FROM platform_admin_bootstrap_invitations WHERE singleton_id = 1"
        private const val SELECT_BY_TOKEN =
            "SELECT $INVITATION_COLUMNS " +
                "FROM platform_admin_bootstrap_invitations WHERE token_hash = :tokenHash"
        private const val SELECT_CLAIMED = """
            SELECT $INVITATION_COLUMNS FROM platform_admin_bootstrap_invitations
            WHERE claimed_principal_id = :principalId AND status = 'CLAIMED'
        """
        private const val REPLACE = """
            INSERT INTO platform_admin_bootstrap_invitations
                (singleton_id, email_normalized, token_hash, expires_at, created_at, status)
            VALUES (1, :email, :tokenHash, :expiresAt, :createdAt, 'PENDING')
            ON CONFLICT (singleton_id) DO UPDATE SET
                email_normalized = EXCLUDED.email_normalized,
                token_hash = EXCLUDED.token_hash,
                expires_at = EXCLUDED.expires_at,
                created_at = EXCLUDED.created_at,
                status = 'PENDING',
                claimed_principal_id = NULL,
                claimed_at = NULL,
                completed_at = NULL
        """
        private const val CLAIM = """
            UPDATE platform_admin_bootstrap_invitations
            SET status = 'CLAIMED', claimed_principal_id = :principalId, claimed_at = :now
            WHERE singleton_id = 1 AND token_hash = :tokenHash
              AND status = 'PENDING' AND expires_at > :now
        """
        private const val COMPLETE = """
            UPDATE platform_admin_bootstrap_invitations
            SET status = 'COMPLETED', completed_at = :now
            WHERE singleton_id = 1 AND claimed_principal_id = :principalId AND status = 'CLAIMED'
        """
    }
}
