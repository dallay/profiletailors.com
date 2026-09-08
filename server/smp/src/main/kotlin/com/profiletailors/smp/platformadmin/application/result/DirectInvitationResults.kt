package com.profiletailors.smp.platformadmin.application.result

import java.util.UUID

data class CreateInvitationResult(val invitationId: UUID, val status: String, val expiresAt: String, val version: Long)

data class RevokeInvitationResult(val invitationId: UUID)

data class ResendInvitationResult(val invitationId: UUID, val status: String, val expiresAt: String, val version: Long)
