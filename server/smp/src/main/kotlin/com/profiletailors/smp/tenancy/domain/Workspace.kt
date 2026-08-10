package com.profiletailors.smp.tenancy.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class WorkspaceStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED,
}

@AggregateRoot
data class Workspace(val id: String, val name: String, val status: WorkspaceStatus, val icon: String? = null) {
    fun isOperational(): Boolean = status == WorkspaceStatus.ACTIVE
}
