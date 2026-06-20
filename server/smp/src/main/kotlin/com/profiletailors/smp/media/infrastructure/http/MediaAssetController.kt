package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.media.application.CreateUploadedAssetCommand
import com.profiletailors.smp.media.application.GetWorkspaceAssetQuery
import com.profiletailors.smp.media.application.ListWorkspaceAssetsQuery
import com.profiletailors.smp.media.application.MediaAssetSummary
import com.profiletailors.smp.media.application.UploadAssetCommand
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import org.springframework.web.server.ResponseStatusException
import com.profiletailors.storage.infrastructure.asFlow
import reactor.core.publisher.Flux

private val logger = LoggerFactory.getLogger(MediaAssetController::class.java)

@Validated
@RestController
@RequestMapping(value = ["/api/media/assets"])
@Tag(name = "Media Library", description = "Workspace-scoped media asset management endpoints")
class MediaAssetController(
    private val mediator: Mediator,
    private val resourceContextProvider: ResourceContextProvider,
) {
    companion object {
        const val MAX_UPLOAD_DURATION_SECONDS = 10L * 60 // 10 minutes
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100
        private const val MIN_PAGE_SIZE = 1
        const val MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB
    }

    @Operation(
        summary = "Create a new uploaded media asset",
        description = "Creates a workspace-scoped asset record in PROCESSING state, ready for binary upload.",
        responses = [
            ApiResponse(
                responseCode = "201",
                description = "Asset created successfully",
                content = [Content(schema = Schema(implementation = MediaAssetResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request (unsupported media type, missing filename for OOXML)",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun createAsset(
        @Valid @RequestBody request: CreateMediaAssetRequest,
    ): MediaAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val sourceType = try {
            MediaSourceType.valueOf(request.sourceType.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported source type: ${request.sourceType}. Only UPLOADED is supported.",
            )
        }

        val command = CreateUploadedAssetCommand(
            workspaceId = workspaceId,
            sourceType = sourceType,
            mediaType = request.mediaType,
            originalFilename = request.originalFilename,
        )

        val result = mediator.send(command)
        logger.info(
            "media.asset.reserved assetId=${result.assetId} workspaceId=$workspaceId " +
                "mediaType=${result.mediaType} sourceType=${result.sourceType}",
        )

        return MediaAssetResponse(
            assetId = result.assetId,
            workspaceId = result.workspaceId,
            sourceType = result.sourceType.name,
            mediaType = result.mediaType,
            status = result.status,
            originalFilename = null,
            fileSizeBytes = null,
            createdAt = java.time.Instant.now().toString(),
        )
    }

    @Operation(
        summary = "Upload binary content to a created asset",
        description = "Streams the binary file to storage and transitions the asset to READY on success.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Upload completed successfully",
                content = [Content(schema = Schema(implementation = MediaAssetResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found or not in active workspace",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Upload conflict — asset already READY or upload in progress",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "413",
                description = "File size exceeds 500 MB limit",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
        ],
    )
    @PostMapping(
        value = ["/{assetId}/upload"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun uploadAsset(
        @Parameter(description = "Asset ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable assetId: String,
        @Parameter(description = "Binary file to upload")
        @RequestPart("file") filePart: org.springframework.http.codec.multipart.FilePart,
    ): MediaAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val headers = filePart.headers()
        val partContentLength = headers.contentLength.takeIf { it > 0 }
        val contentType = headers.contentType?.toString()

        // Pre-check Content-Length header against 500 MB limit
        if (partContentLength != null && partContentLength > MAX_FILE_SIZE_BYTES) {
            logger.warn(
                "media.asset.upload.rejected contentLength={} maxAllowed={} assetId={}",
                partContentLength,
                MAX_FILE_SIZE_BYTES,
                assetId,
            )
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File size exceeds the 500 MB limit.",
            )
        }

        // Bridge Reactor Flux<DataBuffer> to Kotlin Flow<ByteArray> for the handler
        val fileFlow: kotlinx.coroutines.flow.Flow<ByteArray> = filePart.content().asFlow()

        val command = UploadAssetCommand(
            assetId = assetId,
            workspaceId = workspaceId,
            fileStream = fileFlow,
            contentLength = partContentLength,
            maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
            contentType = contentType,
            timeoutSeconds = MAX_UPLOAD_DURATION_SECONDS,
        )

        val result = mediator.send(command)

        return MediaAssetResponse(
            assetId = result.assetId,
            workspaceId = result.workspaceId,
            sourceType = result.sourceType,
            mediaType = result.mediaType,
            status = result.status,
            originalFilename = result.originalFilename,
            fileSizeBytes = result.fileSizeBytes,
            createdAt = result.createdAt,
        )
    }

    @Operation(
        summary = "List workspace media assets",
        description = "Returns assets belonging to the active workspace, newest-first. " +
            "Defaults to READY assets only; use the status parameter to filter.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "List of assets",
                content = [Content(schema = Schema(implementation = MediaAssetListResponse::class))],
            ),
        ],
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun listAssets(
        @Parameter(description = "Asset status filter (READY, PROCESSING, FAILED). Comma-separated for multiple.")
        @RequestParam(required = false, defaultValue = "READY") status: String? = null,

        @Parameter(description = "Number of assets per page (max 100)")
        @RequestParam(required = false, defaultValue = "50")
        @Min(MIN_PAGE_SIZE.toLong())
        @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE,

        @Parameter(description = "Opaque cursor for pagination")
        @RequestParam(required = false) cursor: String? = null,
    ): MediaAssetListResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val statuses = parseStatuses(status)
        val effectivePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)

        val query = ListWorkspaceAssetsQuery(
            workspaceId = workspaceId,
            statuses = statuses,
            pageSize = effectivePageSize,
            cursor = cursor,
        )

        val result = mediator.send(query)

        return MediaAssetListResponse(
            assets = result.assets.map { it.toResponse() },
            nextCursor = result.nextCursor,
        )
    }

    @Operation(
        summary = "Get a single media asset",
        description = "Returns asset details for the given asset ID within the active workspace.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Asset details",
                content = [Content(schema = Schema(implementation = MediaAssetResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found or not in active workspace",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
        ],
    )
    @GetMapping(
        value = ["/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun getAsset(
        @Parameter(description = "Asset ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable assetId: String,
    ): MediaAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val query = GetWorkspaceAssetQuery(
            assetId = assetId,
            workspaceId = workspaceId,
        )

        val result = mediator.send(query)
        return result.toResponse()
    }

    private fun parseStatuses(status: String?): Set<MediaAssetStatus> {
        if (status.isNullOrBlank()) {
            return setOf(MediaAssetStatus.READY)
        }
        return status.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .mapNotNull {
                try {
                    MediaAssetStatus.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .toSet()
            .ifEmpty { setOf(MediaAssetStatus.READY) }
    }
}

private fun MediaAssetSummary.toResponse() = MediaAssetResponse(
    assetId = assetId,
    workspaceId = workspaceId,
    sourceType = sourceType,
    mediaType = mediaType,
    status = status,
    originalFilename = originalFilename,
    fileSizeBytes = fileSizeBytes,
    createdAt = createdAt,
)
