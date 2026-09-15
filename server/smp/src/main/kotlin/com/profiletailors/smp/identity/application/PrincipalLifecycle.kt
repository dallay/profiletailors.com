package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.PrincipalStatus

class PrincipalNotFoundException(principalId: String) : RuntimeException("Principal '$principalId' not found.")

class PrincipalVersionConflictException(principalId: String, expectedVersion: Long, actualVersion: Long) :
    RuntimeException(
        "Principal '$principalId' version conflict: expected $expectedVersion but found $actualVersion.",
    )

class InvalidPrincipalStatusTransitionException(principalId: String, from: PrincipalStatus, to: PrincipalStatus) :
    RuntimeException("Principal '$principalId' cannot transition from $from to $to.")

enum class PrincipalStatusTransition {
    TRANSITIONED,
    ALREADY_IN_TARGET_STATE,
}

interface PrincipalLifecycle {
    suspend fun deactivate(principalId: String, expectedVersion: Long): PrincipalStatusTransition
    suspend fun reactivate(principalId: String, expectedVersion: Long): PrincipalStatusTransition
    suspend fun revokeSessions(principalId: String)
}
