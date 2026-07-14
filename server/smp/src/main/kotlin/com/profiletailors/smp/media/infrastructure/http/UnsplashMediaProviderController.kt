package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.media.application.ImportUnsplashPhotoCommand
import com.profiletailors.smp.media.application.SearchUnsplashPhotosQuery
import com.profiletailors.smp.media.application.UnsplashPhoto
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/media/providers/unsplash/photos")
@Tag(name = "Media Providers", description = "Workspace-scoped external media provider endpoints")
class UnsplashMediaProviderController(
    private val mediator: Mediator,
    private val resourceContextProvider: ResourceContextProvider,
) {
    /**
     * Browses editorial Unsplash photos or searches for photos matching a term.
     *
     * @param query Optional search term used to filter the photos.
     * @return The matching Unsplash photo results.
     */
    @GetMapping
    @Operation(summary = "Browse editorial Unsplash photos or search by term")
    suspend fun search(
        @RequestParam(required = false)
        @Size(max = MAX_QUERY_LENGTH)
        query: String? = null,
    ): UnsplashSearchResponse = UnsplashSearchResponse(
        photos = mediator.send(SearchUnsplashPhotosQuery(query)).map(UnsplashPhoto::toResponse),
    )

    /**
     * Imports an Unsplash photo into the active workspace media library.
     *
     * @param externalId The external identifier of the Unsplash photo to import.
     * @return The imported media asset.
     */
    @PostMapping("/{externalId}/import")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import an Unsplash photo into the active workspace media library")
    suspend fun import(@PathVariable externalId: String): MediaAssetResponse {
        val workspaceId = resourceContextProvider.requireWorkspaceContext().workspaceId!!
        return mediator.send(
            ImportUnsplashPhotoCommand(
                workspaceId = workspaceId,
                externalId = externalId,
            ),
        ).toMediaAssetResponse()
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
    }
}

data class UnsplashSearchResponse(val photos: List<UnsplashPhotoResponse>)

data class UnsplashPhotoResponse(
    val externalId: String,
    val name: String,
    val previewUrl: String,
    val sourceUrl: String,
    val authorName: String,
    val authorUrl: String,
)

/**
 * Converts an Unsplash photo to its HTTP response representation.
 *
 * @return The photo response representation.
 */
private fun UnsplashPhoto.toResponse() = UnsplashPhotoResponse(
    externalId = externalId,
    name = name,
    previewUrl = previewUrl,
    sourceUrl = sourceUrl,
    authorName = authorName,
    authorUrl = authorUrl,
)
