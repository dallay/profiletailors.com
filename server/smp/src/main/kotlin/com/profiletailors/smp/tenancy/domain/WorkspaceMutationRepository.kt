package com.profiletailors.smp.tenancy.domain

interface WorkspaceMutationRepository {
    suspend fun rename(workspaceId: String, newName: String): Boolean

    suspend fun updateIcon(workspaceId: String, icon: String?): Boolean
}
