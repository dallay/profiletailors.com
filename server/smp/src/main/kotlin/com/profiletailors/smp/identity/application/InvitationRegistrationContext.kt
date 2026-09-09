package com.profiletailors.smp.identity.application

data class InvitationRegistrationContext(
    val invitationId: String,
    val target: InvitationRegistrationTarget,
    val workspaceId: String?,
    val source: InvitationRegistrationSource,
) {
    init {
        require(invitationId.isNotBlank()) { "Invitation id must not be blank" }
        when (target) {
            InvitationRegistrationTarget.EXISTING_WORKSPACE -> require(!workspaceId.isNullOrBlank()) {
                "Existing workspace invitations require workspaceId"
            }

            InvitationRegistrationTarget.NEW_WORKSPACE -> require(workspaceId == null) {
                "New workspace invitations must not carry a workspaceId before completion"
            }
        }
    }
}

enum class InvitationRegistrationTarget {
    EXISTING_WORKSPACE,
    NEW_WORKSPACE,
}

enum class InvitationRegistrationSource {
    DIRECT,
    WAITLIST,
}

data class InvitationRegistrationResult(val workspaceId: String, val membershipStatus: String) {
    init {
        require(workspaceId.isNotBlank()) { "Invitation registration requires a resolved workspaceId" }
        require(membershipStatus.isNotBlank()) { "Invitation registration requires a membership status" }
    }
}
