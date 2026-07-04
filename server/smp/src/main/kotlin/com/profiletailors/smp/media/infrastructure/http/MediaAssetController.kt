package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.media.application.CasUploadAssetCommand
import com.profiletailors.smp.media.application.CasUploadAssetResult
import com.profiletailors.smp.media.application.CreateUploadedAssetCommand
import com.profiletailors.smp.media.application.DeleteAssetCommand
import com.profiletailors.smp.media.application.DeleteWorkspaceAssetCommand
import com.profiletailors.smp.media.application.GetWorkspaceAssetQuery
import com.profiletailors.smp.media.application.LegacyUploadAssetCommand
import com.profiletailors.smp.media.application.ListWorkspaceAssetsQuery
import com.profiletailors.smp.media.application.MediaAssetSummary
import com.profiletailors.smp.media.application.PutAssetCommand
import com.profiletailors.smp.media.application.PutAssetResult
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import com.profiletailors.storage.infrastructure.asFlow
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
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

    // ─── CAS PUT /api/workspaces/{workspaceId}/media/assets/{assetId} ──────────

    @Operation(
        summary = "Register a media asset with CAS dedup check",
        description = "Checks for existing blobs by (workspaceId, fileHash) and determines whether to " +
            "fast-path (dedup hit) or require an upload. First step of the CAS upload flow.",
        responses = [
            ApiResponse(responseCode = "201", description = "New asset created — upload to uploadUrl"),
            ApiResponse(responseCode = "200", description = "Idempotent PUT — same assetId + same hash"),
            ApiResponse(responseCode = "202", description = "Blob being uploaded by another request"),
            ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Hash mismatch",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
        ],
    )
    @PutMapping(value = ["/{assetId}"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun putAsset(
        @Parameter(description = "Asset ID (client-generated UUID v4)")
        @PathVariable assetId: String,
        @Valid @RequestBody request: PutAssetRequest,
    ): ResponseEntity<*> {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val command = PutAssetCommand(
            assetId = assetId,
            workspaceId = workspaceId,
            fileHash = request.fileHash,
            fileSizeBytes = request.fileSizeBytes,
            declaredMediaType = request.declaredMediaType,
            originalFilename = request.originalFilename,
        )

        return when (val result = mediator.send(command)) {
            is PutAssetResult.Created -> {
                ResponseEntity.status(HttpStatus.CREATED).body(
                    mapOf(
                        "assetId" to result.assetId,
                        "workspaceId" to result.workspaceId,
                        "status" to result.status,
                        "mediaType" to result.mediaType,
                        "deduped" to result.deduped,
                        "uploadUrl" to result.uploadUrl,
                        "createdAt" to result.createdAt,
                    ),
                )
            }

            is PutAssetResult.AlreadyExists -> {
                ResponseEntity.ok(
                    mapOf(
                        "assetId" to result.assetId,
                        "workspaceId" to result.workspaceId,
                        "status" to result.status,
                        "mediaType" to result.mediaType,
                        "deduped" to result.deduped,
                        "createdAt" to result.createdAt,
                    ),
                )
            }

            is PutAssetResult.WaitingForBlob -> {
                ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("Retry-After", result.retryAfterSeconds.toString())
                    .body(
                        mapOf(
                            "status" to "WAITING_FOR_BLOB",
                            "message" to "Another upload for this file hash is in progress",
                            "retryAfterSeconds" to result.retryAfterSeconds,
                        ),
                    )
            }

            is PutAssetResult.HashMismatch -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    MediaErrorResponse(
                        errorCode = "ASSET_HASH_MISMATCH",
                        message = "Asset already exists with a different file hash",
                        existingFileHash = result.existingFileHash,
                    ),
                )
            }
        }
    }

    // ─── CAS POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload ──

    @Operation(
        summary = "Upload binary content to a CAS asset",
        description = "Streams raw bytes to temp storage, computes SHA-256, validates magic bytes, " +
            "then copies to the canonical CAS key. Must be called after PUT.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Upload completed",
                content = [Content(schema = Schema(implementation = UploadAssetResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found or deleted",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Upload already in progress",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "422",
                description = "Hash or file size mismatch",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
        ],
    )
    @PostMapping(value = ["/{assetId}/upload"], consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    suspend fun uploadAsset(
        @Parameter(description = "Asset ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable assetId: String,
        @RequestBody flux: Flux<org.springframework.core.io.buffer.DataBuffer>,
    ): UploadAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        // Retrieve the asset to get the declared hash, size, and media type
        val assetSummary = mediator.send(GetWorkspaceAssetQuery(assetId = assetId, workspaceId = workspaceId))

        if (assetSummary.fileHash == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Asset has no fileHash — call PUT endpoint first",
            )
        }

        val fileFlow: kotlinx.coroutines.flow.Flow<ByteArray> = flux.asFlow()

        val command = CasUploadAssetCommand(
            assetId = assetId,
            workspaceId = workspaceId,
            fileStream = fileFlow,
            declaredFileHash = assetSummary.fileHash,
            declaredFileSizeBytes = assetSummary.fileSizeBytes ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Asset has no declared file size — call PUT endpoint first",
            ),
            declaredMediaType = assetSummary.mediaType,
            timeoutSeconds = MAX_UPLOAD_DURATION_SECONDS,
        )

        return when (val result = mediator.send(command)) {
            is CasUploadAssetResult.Ready -> {
                UploadAssetResponse(
                    assetId = result.assetId,
                    workspaceId = result.workspaceId,
                    status = result.status,
                    mediaType = result.mediaType,
                    detectedMediaType = result.detectedMediaType,
                    deduped = result.deduped,
                    fileSizeBytes = result.fileSizeBytes,
                    createdAt = result.createdAt,
                )
            }

            is CasUploadAssetResult.UploadInProgress -> {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An upload is already in progress for this asset",
                )
            }

            is CasUploadAssetResult.NotFound -> {
                throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Asset not found or has been deleted",
                )
            }
        }
    }

    // ─── CAS DELETE /api/workspaces/{workspaceId}/media/assets/{assetId} ────────

    @Operation(
        summary = "Delete a media asset (soft-delete)",
        description = "Soft-deletes the asset and schedules the underlying blob for GC if no other " +
            "active assets reference it.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Deleted",
                content = [Content(schema = Schema(implementation = DeleteAssetResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found",
                content = [Content(schema = Schema(implementation = MediaErrorResponse::class))],
            ),
        ],
    )
    @DeleteMapping(value = ["/{assetId}"])
    suspend fun deleteAsset(
        @Parameter(description = "Asset ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable assetId: String,
    ): DeleteAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val result = mediator.send(DeleteAssetCommand(assetId = assetId, workspaceId = workspaceId))
        return DeleteAssetResponse(
            deleted = result.deleted,
            blobScheduledForGC = result.blobScheduledForGC,
        )
    }

    // ─── Legacy endpoints (backward compatibility) ─────────────────────────────

    @Operation(
        summary = "[Legacy] Create a new uploaded media asset",
        responses = [
            ApiResponse(responseCode = "201", description = "Asset created"),
            ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], version = "1")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createAsset(@Valid @RequestBody request: CreateMediaAssetRequest): MediaAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val sourceType = try {
            MediaSourceType.valueOf(request.sourceType.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported source type: ${request.sourceType}. Supported source types: UPLOADED, EXTERNAL.",
            )
        }

        val result = mediator.send(
            CreateUploadedAssetCommand(
                workspaceId = workspaceId,
                sourceType = sourceType,
                mediaType = request.mediaType,
                originalFilename = request.originalFilename,
            ),
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

    @PostMapping(
        value = ["/{assetId}/upload"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        version = "1",
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

        val fileFlow: kotlinx.coroutines.flow.Flow<ByteArray> = filePart.content().asFlow()

        val command = LegacyUploadAssetCommand(
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

    @Operation(summary = "List workspace media assets")
    @GetMapping(version = "1")
    suspend fun listAssets(
        @RequestParam(required = false, defaultValue = "READY") status: String? = null,
        @RequestParam(required = false, defaultValue = "50")
        @Min(MIN_PAGE_SIZE.toLong())
        @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) cursor: String? = null,
    ): MediaAssetListResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!

        val query = ListWorkspaceAssetsQuery(
            workspaceId = workspaceId,
            statuses = parseStatuses(status),
            pageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = cursor,
        )

        val result = mediator.send(query)
        return MediaAssetListResponse(
            assets = result.assets.map { it.toResponse() },
            nextCursor = result.nextCursor,
        )
    }

    @Operation(summary = "Get a single media asset")
    @GetMapping(value = ["/{assetId}"], version = "1")
    suspend fun getAsset(@PathVariable assetId: String): MediaAssetResponse {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!
        return mediator.send(GetWorkspaceAssetQuery(assetId = assetId, workspaceId = workspaceId)).toResponse()
    }

    @DeleteMapping(value = ["/{assetId}"], version = "1")
    suspend fun deleteAssetLegacy(@PathVariable assetId: String): ResponseEntity<Void> {
        val workspaceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = workspaceContext.workspaceId!!
        mediator.send(DeleteWorkspaceAssetCommand(assetId = assetId, workspaceId = workspaceId))
        return ResponseEntity.noContent().build()
    }

    private fun parseStatuses(status: String?): Set<MediaAssetStatus> {
        if (status.isNullOrBlank()) return setOf(MediaAssetStatus.READY)
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
    previewUrl = previewUrl,
    downloadUrl = downloadUrl,
    sourceProvider = sourceProvider,
    externalId = externalId,
    sourceUrl = sourceUrl,
    authorName = authorName,
    authorUrl = authorUrl,
    metadata = metadata,
)
