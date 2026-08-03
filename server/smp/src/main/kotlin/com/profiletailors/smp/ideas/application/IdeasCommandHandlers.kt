package com.profiletailors.smp.ideas.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.ideas.domain.Idea
import com.profiletailors.smp.ideas.domain.IdeaBoardConfig
import com.profiletailors.smp.ideas.domain.IdeaBoardConfigRepository
import com.profiletailors.smp.ideas.domain.IdeaBoardDefaults
import com.profiletailors.smp.ideas.domain.IdeaPolicies
import com.profiletailors.smp.ideas.domain.IdeaRepository
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.util.UUID

@Service
internal class CreateIdeaHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
    private val boardConfigRepository: IdeaBoardConfigRepository,
    private val clock: Clock,
) : CommandWithResultHandler<CreateIdeaCommand, IdeaResult> {
    override suspend fun handle(command: CreateIdeaCommand): IdeaResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val now = clock.instant()
        val board = ensureBoard(workspaceId)

        val requestedColumnId = command.columnId
        val targetColumnId = when {
            requestedColumnId != null -> requestedColumnId
            board.columns.isNotEmpty() -> board.columns.minBy { it.order }.id
            else -> IdeaBoardDefaults.columns.first().id
        }

        val currentIdeas = ideaRepository.listByWorkspace(workspaceId)
        val nextOrder = currentIdeas.count { it.columnId == targetColumnId }

        val idea = Idea(
            id = "idea-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            title = command.title.trim(),
            notes = command.notes,
            tags = command.tags,
            links = command.links,
            columnId = targetColumnId,
            orderInColumn = nextOrder,
            convertedToPublicationId = null,
            createdAt = now,
            updatedAt = now,
        )

        return ideaRepository.create(idea).toResult()
    }

    private suspend fun ensureBoard(workspaceId: String): IdeaBoardConfig =
        boardConfigRepository.findByWorkspace(workspaceId)
            ?: boardConfigRepository.upsert(
                IdeaBoardConfig(workspaceId, IdeaBoardDefaults.columns),
            )
}

@Service
internal class UpdateIdeaHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
    private val clock: Clock,
) : CommandWithResultHandler<UpdateIdeaCommand, IdeaResult> {
    override suspend fun handle(command: UpdateIdeaCommand): IdeaResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val existing = ideaRepository.findByWorkspaceAndId(workspaceId, command.ideaId)
            ?: throw IdeaNotFoundException(command.ideaId)

        val updated = existing.copy(
            title = command.title?.trim() ?: existing.title,
            notes = command.notes ?: existing.notes,
            tags = command.tags ?: existing.tags,
            links = command.links ?: existing.links,
            columnId = command.columnId ?: existing.columnId,
            updatedAt = clock.instant(),
        )

        return ideaRepository.update(updated).toResult()
    }
}

@Service
internal class MoveIdeaHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
    private val clock: Clock,
) : CommandWithResultHandler<MoveIdeaCommand, IdeaResult> {
    override suspend fun handle(command: MoveIdeaCommand): IdeaResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val existing = ideaRepository.findByWorkspaceAndId(workspaceId, command.ideaId)
            ?: throw IdeaNotFoundException(command.ideaId)

        val updated = existing.copy(
            columnId = command.columnId,
            orderInColumn = command.orderInColumn.coerceAtLeast(0),
            updatedAt = clock.instant(),
        )

        val saved = ideaRepository.update(updated)

        val normalized = IdeaPolicies.normalizeIdeasInColumns(ideaRepository.listByWorkspace(workspaceId))
        normalized.forEach { ideaRepository.update(it) }

        return saved.toResult()
    }
}

@Service
internal class DeleteIdeaHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
) : CommandWithResultHandler<DeleteIdeaCommand, IdeaResult> {
    override suspend fun handle(command: DeleteIdeaCommand): IdeaResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val existing = ideaRepository.findByWorkspaceAndId(workspaceId, command.ideaId)
            ?: throw IdeaNotFoundException(command.ideaId)

        val deleted = ideaRepository.delete(workspaceId, command.ideaId)
        if (!deleted) {
            throw IdeaNotFoundException(command.ideaId)
        }

        return existing.toResult()
    }
}

@Service
@Suppress("LongParameterList")
internal class ConvertIdeaHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
    private val mediator: com.profiletailors.common.domain.bus.Mediator,
    private val clock: Clock,
) : CommandWithResultHandler<ConvertIdeaCommand, ConvertIdeaResult> {
    override suspend fun handle(command: ConvertIdeaCommand): ConvertIdeaResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val idea = ideaRepository.findByWorkspaceAndId(workspaceId, command.ideaId)
            ?: throw IdeaNotFoundException(command.ideaId)

        val fallbackBody = buildString {
            append(idea.title)
            if (!idea.notes.isNullOrBlank()) {
                append("\n\n")
                append(idea.notes)
            }
            if (idea.tags.isNotEmpty()) {
                append("\n\n")
                append(idea.tags.joinToString(" ") { "#$it" })
            }
        }

        val publication: PublicationResult = mediator.send(
            CreatePublicationCommand.now(
                socialAccountId = requireConnectedAccountId(),
                title = idea.title,
                bodyText = fallbackBody,
            ),
        )

        ideaRepository.update(
            idea.copy(
                convertedToPublicationId = publication.publicationId,
                updatedAt = clock.instant(),
            ),
        )

        return ConvertIdeaResult(ideaId = idea.id, publicationId = publication.publicationId)
    }

    private suspend fun requireConnectedAccountId(): String {
        val channels = mediator.send(ListConnectedChannelsQuery.active())
        return channels.channels.firstOrNull()?.socialAccountId
            ?: throw InvalidIdeaColumnsException("At least one active social channel is required to convert an idea.")
    }
}

@Service
internal class UpdateColumnsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val boardConfigRepository: IdeaBoardConfigRepository,
    private val ideaRepository: IdeaRepository,
) : CommandWithResultHandler<UpdateColumnsCommand, ColumnsResponse> {
    override suspend fun handle(command: UpdateColumnsCommand): ColumnsResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        if (command.columns.isEmpty()) {
            throw InvalidIdeaColumnsException("At least one column is required.")
        }

        val normalized = IdeaPolicies.normalizeColumns(command.columns)
        val saved = boardConfigRepository.upsert(
            IdeaBoardConfig(
                workspaceId = workspaceId,
                columns = normalized,
            ),
        )

        val validIds = saved.columns.map { it.id }.toSet()
        val fallback = saved.columns.minByOrNull { it.order }?.id ?: IdeaBoardDefaults.columns.first().id
        val normalizedIdeas = IdeaPolicies.normalizeIdeasInColumns(
            ideaRepository.listByWorkspace(workspaceId).map { idea ->
                if (idea.columnId in validIds) idea else idea.copy(columnId = fallback)
            },
        )
        normalizedIdeas.forEach { ideaRepository.update(it) }

        return ColumnsResponse(saved.columns)
    }
}
