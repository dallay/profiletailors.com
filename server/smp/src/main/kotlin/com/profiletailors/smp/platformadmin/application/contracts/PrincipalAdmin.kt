package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.identity.domain.PrincipalStatus

interface PrincipalAdmin {
    suspend fun findById(principalId: String): PrincipalSummary?
    suspend fun updateStatus(principalId: String, status: PrincipalStatus): Boolean
}

data class PrincipalSummary(
    val principalId: String,
    val principalType: String,
    val status: PrincipalStatus,
    val version: Long,
)
