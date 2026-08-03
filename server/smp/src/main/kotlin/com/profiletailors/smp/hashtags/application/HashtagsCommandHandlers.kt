package com.profiletailors.smp.hashtags.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.hashtags.domain.HashtagSavedSet
import com.profiletailors.smp.hashtags.domain.HashtagSavedSetRepository
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.util.UUID

@Service
internal class SaveHashtagSetHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val repository: HashtagSavedSetRepository,
    private val clock: Clock,
) : CommandWithResultHandler<SaveHashtagSetCommand, HashtagSavedSetResult> {
    override suspend fun handle(command: SaveHashtagSetCommand): HashtagSavedSetResult {
        if (command.name.isBlank()) throw HashtagSetNameBlankException()
        if (command.hashtags.isEmpty()) throw HashtagSetEmptyException()

        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val now = clock.instant()
        val set = HashtagSavedSet(
            id = "htset-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            name = command.name.trim(),
            hashtags = command.hashtags.map { normalizeHashtag(it) },
            createdAt = now,
            updatedAt = now,
        )
        return repository.create(set).toResult()
    }

    private fun normalizeHashtag(tag: String): String = if (tag.startsWith('#')) tag else "#$tag"
}

@Service
internal class DeleteHashtagSetHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val repository: HashtagSavedSetRepository,
) : CommandWithResultHandler<DeleteHashtagSetCommand, Unit> {
    override suspend fun handle(command: DeleteHashtagSetCommand) {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val deleted = repository.delete(workspaceId, command.setId)
        if (!deleted) throw HashtagSavedSetNotFoundException(command.setId)
    }
}
