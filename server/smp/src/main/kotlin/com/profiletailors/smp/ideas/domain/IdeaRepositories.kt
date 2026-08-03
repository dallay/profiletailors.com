package com.profiletailors.smp.ideas.domain

interface IdeaRepository {
    suspend fun listByWorkspace(workspaceId: String): List<Idea>

    suspend fun findByWorkspaceAndId(workspaceId: String, ideaId: String): Idea?

    suspend fun create(idea: Idea): Idea

    suspend fun update(idea: Idea): Idea

    suspend fun delete(workspaceId: String, ideaId: String): Boolean
}

interface IdeaBoardConfigRepository {
    suspend fun findByWorkspace(workspaceId: String): IdeaBoardConfig?

    suspend fun upsert(config: IdeaBoardConfig): IdeaBoardConfig
}
