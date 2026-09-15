package com.profiletailors.smp.tenancy.application

fun interface WorkspaceNameReader {
    suspend fun findName(workspaceId: String): String?
}
