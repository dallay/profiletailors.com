package com.profiletailors.smp.identity.domain

data class ExternalIdentityLink(
    val issuer: String,
    val subject: String,
    val principalId: String,
    val status: ExternalIdentityLinkStatus,
)

enum class ExternalIdentityLinkStatus {
    APPROVED,
    DISABLED,
    DISALLOWED,
}
