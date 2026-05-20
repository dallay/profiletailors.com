package com.profiletailors.smp.tenancy.domain

enum class WorkspaceStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED,
}

data class Workspace(
    val id: String,
    val name: String,
    val status: WorkspaceStatus,
) {
    fun isOperational(): Boolean = status == WorkspaceStatus.ACTIVE
}
