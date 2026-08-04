package com.profiletailors.smp.hashtags.domain

interface HashtagSavedSetRepository {
    suspend fun listByWorkspace(workspaceId: String): List<HashtagSavedSet>

    suspend fun findByWorkspaceAndId(workspaceId: String, setId: String): HashtagSavedSet?

    suspend fun create(set: HashtagSavedSet): HashtagSavedSet

    suspend fun delete(workspaceId: String, setId: String): Boolean
}
