package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcInvitationRepository(private val databaseClient: DatabaseClient) : InvitationRepository {

    /**
         * Finds an invitation by its identifier.
         *
         * @param id The invitation identifier to search for.
         * @return The matching invitation, or `null` if no invitation exists with the identifier.
         */
        override suspend fun findById(id: InvitationId): Invitation? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id.value)
        .map { row, _ -> row.toInvitation() }
        .one()
        .awaitSingleOrNull()

    /**
         * Finds an invitation by candidate key while locking the matching database row for update.
         *
         * @param candidateKey The key identifying the candidate.
         * @return The matching invitation, or `null` if no invitation exists for the candidate key.
         */
        override suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation? = databaseClient.sql(
        SELECT_BY_CANDIDATE_KEY_FOR_UPDATE,
    )
        .bind("candidateKey", candidateKey)
        .map { row, _ -> row.toInvitation() }
        .one()
        .awaitSingleOrNull()

    /**
     * Persists an invitation and reloads the stored invitation.
     *
     * @param invitation The invitation to persist.
     * @param candidateKey The candidate key associated with the invitation.
     * @return The persisted invitation.
     * @throws IllegalStateException If the persisted invitation cannot be reloaded.
     */
    override suspend fun save(invitation: Invitation, candidateKey: String): Invitation {
        databaseClient.sql(INSERT)
            .bind("id", invitation.id.value)
            .bind("source", invitation.source.name)
            .bindNullableString("sourceReferenceId", invitation.sourceReferenceId)
            .bind("workspaceId", invitation.workspaceId)
            .bind("invitedEmailNormalized", invitation.invitedEmailNormalized)
            .bind("candidateKey", candidateKey)
            .bind("tokenHash", invitation.tokenHash)
            .bind("status", invitation.status.name)
            .bind("issuedBy", invitation.issuedBy)
            .bind("createdAt", OffsetDateTime.ofInstant(invitation.createdAt, ZoneOffset.UTC))
            .bind("expiresAt", OffsetDateTime.ofInstant(invitation.expiresAt, ZoneOffset.UTC))
            .bindNullableInstant("acceptedAt", invitation.acceptedAt)
            .bindNullableString("acceptedPrincipalId", invitation.acceptedPrincipalId)
            .bind("version", invitation.version)
            .then()
            .awaitSingleOrNull()
        return requireNotNull(findById(invitation.id)) {
            "Invitation ${invitation.id.value} was just persisted but cannot be reloaded"
        }
    }

    /**
     * Updates an invitation when its current version matches the expected prior version.
     *
     * @param invitation The invitation containing the updated values and version.
     * @return `true` if exactly one invitation was updated, `false` if the version is zero or no matching invitation exists.
     */
    override suspend fun updateIfVersionMatches(invitation: Invitation): Boolean {
        if (invitation.version == 0L) return false
        val expectedVersion = invitation.version - 1
        val rowsUpdated = databaseClient.sql(UPDATE_IF_VERSION_MATCHES)
            .bind("status", invitation.status.name)
            .bindNullableInstant("acceptedAt", invitation.acceptedAt)
            .bindNullableString("acceptedPrincipalId", invitation.acceptedPrincipalId)
            .bind("version", invitation.version)
            .bind("id", invitation.id.value)
            .bind("expectedVersion", expectedVersion)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return rowsUpdated == 1L
    }

    /**
     * Maps database columns to an invitation.
     *
     * @return The invitation represented by the row.
     */
    private fun Readable.toInvitation(): Invitation = Invitation(
        id = InvitationId(requireNotNull(get("id", UUID::class.java))),
        source = InvitationSource.valueOf(requireNotNull(get("source", String::class.java))),
        sourceReferenceId = get("source_reference_id", String::class.java),
        workspaceId = requireNotNull(get("workspace_id", String::class.java)),
        invitedEmailNormalized = requireNotNull(get("invited_email_normalized", String::class.java)),
        tokenHash = requireNotNull(get("token_hash", String::class.java)),
        status = InvitationStatus.valueOf(requireNotNull(get("status", String::class.java))),
        issuedBy = requireNotNull(get("issued_by", String::class.java)),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        expiresAt = requireNotNull(get("expires_at", OffsetDateTime::class.java)).toInstant(),
        acceptedAt = get("accepted_at", OffsetDateTime::class.java)?.toInstant(),
        acceptedPrincipalId = get("accepted_principal_id", String::class.java),
        version = requireNotNull(get("version", Long::class.javaObjectType)),
    )

    companion object {
        private const val COLUMNS = """
            id, source, source_reference_id, workspace_id, invited_email_normalized,
            candidate_key, token_hash, status, issued_by, created_at, expires_at,
            accepted_at, accepted_principal_id, version
        """
        private const val SELECT_BY_ID = "SELECT $COLUMNS FROM invitations WHERE id = :id"
        private const val SELECT_BY_CANDIDATE_KEY_FOR_UPDATE = """
            SELECT $COLUMNS
            FROM invitations
            WHERE candidate_key = :candidateKey
            FOR UPDATE
        """
        private const val INSERT = """
            INSERT INTO invitations (
                id, source, source_reference_id, workspace_id, invited_email_normalized,
                candidate_key, token_hash, status, issued_by, created_at, expires_at,
                accepted_at, accepted_principal_id, version
            ) VALUES (
                :id, :source, :sourceReferenceId, :workspaceId, :invitedEmailNormalized,
                :candidateKey, :tokenHash, :status, :issuedBy, :createdAt, :expiresAt,
                :acceptedAt, :acceptedPrincipalId, :version
            )
        """
        private const val UPDATE_IF_VERSION_MATCHES = """
            UPDATE invitations
            SET status = :status,
                accepted_at = :acceptedAt,
                accepted_principal_id = :acceptedPrincipalId,
                version = :version
            WHERE id = :id AND version = :expectedVersion
        """
    }
}

/**
 * Binds a string value or a typed SQL `NULL` when the value is absent.
 *
 * @param name The name of the parameter to bind.
 * @param value The string value to bind, or `null`.
 * @return This execute specification with the parameter bound.
 */
private fun DatabaseClient.GenericExecuteSpec.bindNullableString(
    name: String,
    value: String?,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, String::class.java)

/**
 * Binds an optional instant as a UTC timestamp or a typed SQL `NULL`.
 *
 * @param name The name of the parameter to bind.
 * @param value The instant to bind, or `null`.
 * @return This execute specification with the parameter bound.
 */
private fun DatabaseClient.GenericExecuteSpec.bindNullableInstant(
    name: String,
    value: Instant?,
): DatabaseClient.GenericExecuteSpec {
    val odt = value?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
    return if (odt != null) bind(name, odt) else bindNull(name, OffsetDateTime::class.java)
}
