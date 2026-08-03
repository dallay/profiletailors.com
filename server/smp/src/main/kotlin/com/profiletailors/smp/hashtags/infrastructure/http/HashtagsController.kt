package com.profiletailors.smp.hashtags.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.hashtags.application.AnalyzeHashtagsQuery
import com.profiletailors.smp.hashtags.application.DeleteHashtagSetCommand
import com.profiletailors.smp.hashtags.application.GetTrendingHashtagsQuery
import com.profiletailors.smp.hashtags.application.HashtagAnalysisResult
import com.profiletailors.smp.hashtags.application.HashtagSavedSetResult
import com.profiletailors.smp.hashtags.application.HashtagSavedSetsResult
import com.profiletailors.smp.hashtags.application.ListHashtagSavedSetsQuery
import com.profiletailors.smp.hashtags.application.SaveHashtagSetCommand
import com.profiletailors.smp.hashtags.application.TrendingHashtagsResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hashtags")
@Tag(name = "Hashtags", description = "AI hashtag suggestion and saved sets")
class HashtagsController(private val mediator: Mediator) {

    @Operation(summary = "Analyze content and return hashtag suggestions")
    @PostMapping("/analyze", consumes = ["application/json"], version = "1")
    suspend fun analyze(@Valid @RequestBody request: AnalyzeContentRequest): HashtagAnalysisResult =
        mediator.send(AnalyzeHashtagsQuery(content = request.content))

    @Operation(summary = "Get trending hashtags")
    @GetMapping("/trending", version = "1")
    suspend fun trending(): TrendingHashtagsResult = mediator.send(GetTrendingHashtagsQuery)

    @Operation(summary = "Save a hashtag set for reuse")
    @PostMapping("/saved-sets", consumes = ["application/json"], version = "1")
    suspend fun saveSet(@Valid @RequestBody request: SaveHashtagSetRequest): ResponseEntity<HashtagSavedSetResult> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(mediator.send(SaveHashtagSetCommand(name = request.name, hashtags = request.hashtags)))

    @Operation(summary = "List saved hashtag sets")
    @GetMapping("/saved-sets", version = "1")
    suspend fun listSets(): HashtagSavedSetsResult = mediator.send(ListHashtagSavedSetsQuery(workspaceId = ""))

    @Operation(summary = "Delete a saved hashtag set")
    @DeleteMapping("/saved-sets/{setId}", version = "1")
    suspend fun deleteSet(@PathVariable setId: String): ResponseEntity<Unit> {
        mediator.send(DeleteHashtagSetCommand(setId = setId))
        return ResponseEntity.noContent().build()
    }
}

data class AnalyzeContentRequest(
    @field:NotBlank
    val content: String,
)

data class SaveHashtagSetRequest(
    @field:NotBlank
    val name: String,
    @field:NotEmpty
    val hashtags: List<String>,
)
