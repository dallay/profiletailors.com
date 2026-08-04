package com.profiletailors.smp.ideas.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.ideas.domain.IdeaLink

data class IdeaResult(
    val id: String,
    val workspaceId: String,
    val title: String,
    val notes: String?,
    val tags: List<String>,
    val links: List<IdeaLink>,
    val columnId: String,
    val orderInColumn: Int,
    val convertedToPublicationId: String?,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant,
)

data object ListIdeasQuery : Query<ListIdeasResponse>

data class ListIdeasResponse(val ideas: List<IdeaResult>)

data class GetIdeaQuery(val ideaId: String) : Query<IdeaResult>

data class CreateIdeaCommand(
    val title: String,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val links: List<IdeaLink> = emptyList(),
    val columnId: String? = null,
) : CommandWithResult<IdeaResult>

data class UpdateIdeaCommand(
    val ideaId: String,
    val title: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
    val links: List<IdeaLink>? = null,
    val columnId: String? = null,
) : CommandWithResult<IdeaResult>

data class MoveIdeaCommand(val ideaId: String, val columnId: String, val orderInColumn: Int) :
    CommandWithResult<IdeaResult>

data class DeleteIdeaCommand(val ideaId: String) : CommandWithResult<IdeaResult>

data class ConvertIdeaCommand(val ideaId: String) : CommandWithResult<ConvertIdeaResult>

data class ConvertIdeaResult(val ideaId: String, val publicationId: String?)

data object GetColumnsQuery : Query<ColumnsResponse>

data class UpdateColumnsCommand(val columns: List<IdeaColumn>) : CommandWithResult<ColumnsResponse>

data class ColumnsResponse(val columns: List<IdeaColumn>)
