package com.profiletailors.smp.ideas.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.ideas.application.ColumnsResponse
import com.profiletailors.smp.ideas.application.ConvertIdeaCommand
import com.profiletailors.smp.ideas.application.ConvertIdeaResult
import com.profiletailors.smp.ideas.application.CreateIdeaCommand
import com.profiletailors.smp.ideas.application.DeleteIdeaCommand
import com.profiletailors.smp.ideas.application.GetColumnsQuery
import com.profiletailors.smp.ideas.application.GetIdeaQuery
import com.profiletailors.smp.ideas.application.IdeaResult
import com.profiletailors.smp.ideas.application.ListIdeasQuery
import com.profiletailors.smp.ideas.application.ListIdeasResponse
import com.profiletailors.smp.ideas.application.MoveIdeaCommand
import com.profiletailors.smp.ideas.application.UpdateColumnsCommand
import com.profiletailors.smp.ideas.application.UpdateIdeaCommand
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.ideas.domain.IdeaLink
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ideas")
@Tag(name = "Ideas", description = "Idea Canvas endpoints")
class IdeasController(private val mediator: Mediator) {
    @Operation(summary = "List ideas")
    @GetMapping(version = "1")
    suspend fun listIdeas(): ListIdeasResponse = mediator.send(ListIdeasQuery)

    @Operation(summary = "Create idea")
    @PostMapping(consumes = ["application/json"], version = "1")
    suspend fun createIdea(@Valid @RequestBody request: CreateIdeaRequest): IdeaResult = mediator.send(
        CreateIdeaCommand(
            title = request.title,
            notes = request.notes,
            tags = request.tags ?: emptyList(),
            links = request.links ?: emptyList(),
            columnId = request.columnId,
        ),
    )

    @Operation(summary = "Get idea by id")
    @GetMapping("/{ideaId}", version = "1")
    suspend fun getIdea(@PathVariable ideaId: String): IdeaResult = mediator.send(GetIdeaQuery(ideaId))

    @Operation(summary = "Update idea")
    @PatchMapping("/{ideaId}", consumes = ["application/json"], version = "1")
    suspend fun updateIdea(@PathVariable ideaId: String, @Valid @RequestBody request: UpdateIdeaRequest): IdeaResult =
        mediator.send(
            UpdateIdeaCommand(
                ideaId = ideaId,
                title = request.title,
                notes = request.notes,
                tags = request.tags,
                links = request.links,
                columnId = request.columnId,
            ),
        )

    @Operation(summary = "Move idea")
    @PatchMapping("/{ideaId}/move", consumes = ["application/json"], version = "1")
    suspend fun moveIdea(@PathVariable ideaId: String, @Valid @RequestBody request: MoveIdeaRequest): IdeaResult =
        mediator.send(
            MoveIdeaCommand(
                ideaId = ideaId,
                columnId = request.columnId,
                orderInColumn = request.orderInColumn,
            ),
        )

    @Operation(summary = "Delete idea")
    @DeleteMapping("/{ideaId}", version = "1")
    suspend fun deleteIdea(@PathVariable ideaId: String): IdeaResult = mediator.send(DeleteIdeaCommand(ideaId))

    @Operation(summary = "Convert idea to publication")
    @PostMapping("/{ideaId}/convert", version = "1")
    suspend fun convertIdea(@PathVariable ideaId: String): ConvertIdeaResult = mediator.send(ConvertIdeaCommand(ideaId))

    @Operation(summary = "Get board columns")
    @GetMapping("/columns", version = "1")
    suspend fun getColumns(): ColumnsResponse = mediator.send(GetColumnsQuery)

    @Operation(summary = "Update board columns")
    @PutMapping("/columns", consumes = ["application/json"], version = "1")
    suspend fun updateColumns(@Valid @RequestBody request: UpdateColumnsRequest): ColumnsResponse =
        mediator.send(UpdateColumnsCommand(columns = request.columns))
}

data class CreateIdeaRequest(
    @field:NotBlank
    val title: String,
    val notes: String? = null,
    val tags: List<String>? = null,
    val links: List<IdeaLink>? = null,
    val columnId: String? = null,
)

data class UpdateIdeaRequest(
    val title: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
    val links: List<IdeaLink>? = null,
    val columnId: String? = null,
)

data class MoveIdeaRequest(
    @field:NotBlank
    val columnId: String,
    val orderInColumn: Int,
)

data class UpdateColumnsRequest(val columns: List<IdeaColumn>)
