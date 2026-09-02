package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId

interface InvitationRepository {
    /**
 * Retrieves an invitation by its identifier.
 *
 * @param id The invitation identifier.
 * @return The matching invitation, or `null` if no invitation is found.
 */
suspend fun findById(id: InvitationId): Invitation?

    /**
 * Retrieves an invitation for update using its candidate key.
 *
 * @param candidateKey The candidate key associated with the invitation.
 * @return The invitation associated with the candidate key, or `null` if none exists.
 */
suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation?

    /**
 * Persists an invitation for a candidate.
 *
 * @param invitation The invitation to persist.
 * @param candidateKey The candidate key associated with the invitation.
 * @return The persisted invitation.
 */
suspend fun save(invitation: Invitation, candidateKey: String): Invitation

    /**
 * Updates an invitation when its current version matches the persisted version.
 *
 * @param invitation The invitation containing the expected version and updated data.
 * @return `true` if the update succeeds, `false` if the versions do not match.
 */
suspend fun updateIfVersionMatches(invitation: Invitation): Boolean
}
