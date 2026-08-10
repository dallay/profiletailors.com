package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepository
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class R2dbcInvitationAcceptanceRepository(private val databaseClient: DatabaseClient) : InvitationAcceptanceRepository {

    override suspend fun findByTokenCandidateKeyForUpdate(candidateKey: String): Invitation? = databaseClient.sql(
        SELECT_BY_CANDIDATE_KEY_FOR_UPDATE,
    )
        .bind("candidateKey", candidateKey)
        .map { row, _ -> row.toInvitation() }
        .one()
        .awaitSingleOrNull()

    override suspend fun markAccepted(invitationId: InvitationId, acceptedAt: Instant, principalId: String): Boolean =
        databaseClient.sql(MARK_ACCEPTED)
            .bind("id", invitationId.value)
            .bind("acceptedAt", acceptedAt)
            .bind("acceptedPrincipalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle() == 1L

    private fun Readable.toInvitation(): Invitation = Invitation(
        id = InvitationId(requireNotNull(get("id", UUID::class.java))),
        source = InvitationSource.valueOf(requireNotNull(get("source", String::class.java))),
        sourceReferenceId = get("source_reference_id", String::class.java),
        workspaceId = requireNotNull(get("workspace_id", String::class.java)),
        invitedEmailNormalized = requireNotNull(get("invited_email_normalized", String::class.java)),
        tokenHash = requireNotNull(get("token_hash", String::class.java)),
        status = InvitationStatus.valueOf(requireNotNull(get("status", String::class.java))),
        issuedBy = requireNotNull(get("issued_by", String::class.java)),
        createdAt = requireNotNull(get("created_at", Instant::class.java)),
        expiresAt = requireNotNull(get("expires_at", Instant::class.java)),
        acceptedAt = get("accepted_at", Instant::class.java),
        acceptedPrincipalId = get("accepted_principal_id", String::class.java),
    )

    companion object {
        private const val COLUMNS = """
            id, source, source_reference_id, workspace_id, invited_email_normalized,
            token_hash, status, issued_by, created_at, expires_at, accepted_at,
            accepted_principal_id
        """
        private const val SELECT_BY_CANDIDATE_KEY_FOR_UPDATE = """
            SELECT $COLUMNS
            FROM invitations
            WHERE candidate_key = :candidateKey
            FOR UPDATE
        """
        private const val MARK_ACCEPTED = """
            UPDATE invitations
            SET status = 'ACCEPTED', accepted_at = :acceptedAt,
                accepted_principal_id = :acceptedPrincipalId
            WHERE id = :id AND status = 'ACTIVE'
        """
    }
}
