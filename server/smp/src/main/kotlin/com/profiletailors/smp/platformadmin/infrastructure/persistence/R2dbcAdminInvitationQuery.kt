package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.contracts.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class R2dbcAdminInvitationQuery(private val invitationRepository: WaitlistInvitationRepository) :
    AdminInvitationQuery {

    override suspend fun findById(invitationId: UUID): AdminInvitationSummary? =
        invitationRepository.findById(WaitlistInvitationId(invitationId))?.toSummary()

    private fun com.profiletailors.smp.platformadmin.domain.WaitlistInvitation.toSummary() = AdminInvitationSummary(
        id = id.value,
        waitlistEntryId = waitlistEntryId,
        status = status.name,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        revokedAt = revokedAt,
        revokedBy = revokedBy,
        createdBy = createdBy,
        deliveryStatus = deliveryStatus.name,
        deliveryAttemptCount = deliveryAttemptCount,
        version = version,
    )
}
