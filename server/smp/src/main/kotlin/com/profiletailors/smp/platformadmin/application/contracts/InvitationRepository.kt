package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId

interface InvitationRepository {
    suspend fun findById(id: InvitationId): Invitation?

    suspend fun findBySourceReferenceId(sourceReferenceId: String): Invitation?

    suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation?

    suspend fun save(invitation: Invitation, candidateKey: String): Invitation

    suspend fun updateIfVersionMatches(invitation: Invitation): Boolean
}
