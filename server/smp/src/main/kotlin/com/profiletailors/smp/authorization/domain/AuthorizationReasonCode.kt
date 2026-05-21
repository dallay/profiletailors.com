package com.profiletailors.smp.authorization.domain

enum class AuthorizationReasonCode {
    ROLE_PERMISSION,
    DIRECT_ALLOW,
    DIRECT_DENY,
    MISSING_MEMBERSHIP,
    MISSING_PERMISSION,
    MISSING_ENTITLEMENT,
    REVOKED_CREDENTIAL,
    SCOPE_REDUCED_TARGET,
}
