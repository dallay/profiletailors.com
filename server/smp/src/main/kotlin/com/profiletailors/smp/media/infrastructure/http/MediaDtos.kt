package com.profiletailors.smp.media.infrastructure.http

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Request to create a new uploaded media asset")
data class CreateMediaAssetRequest(
    @field:NotBlank
    @field:Schema(
        description = "Source type, must be 'UPLOADED' for this MVP",
        example = "UPLOADED",
    )
    val sourceType: String,

    @field:NotBlank
    @field:Schema(
        description = "MIME type of the file (e.g., 'image/jpeg')",
        example = "image/jpeg",
    )
    val mediaType: String,

    @field:Schema(
        description = "Original filename. Required for OOXML formats (.docx, .pptx, .doc, .ppt)",
        example = "launch-post.jpg",
        required = false,
    )
    val originalFilename: String? = null,
)

@Schema(description = "Summary of a media asset returned in list or single-get responses")
data class MediaAssetResponse(
    @field:Schema(description = "Unique asset identifier (UUID v4)", example = "550e8400-e29b-41d4-a716-446655440000")
    val assetId: String,

    @field:Schema(description = "Workspace that owns this asset", example = "ws-123")
    val workspaceId: String,

    @field:Schema(description = "Source type", example = "UPLOADED")
    val sourceType: String,

    @field:Schema(description = "MIME type of the file", example = "image/jpeg")
    val mediaType: String,

    @field:Schema(description = "Current lifecycle status", example = "PROCESSING")
    val status: String,

    @field:Schema(description = "Original filename if provided", required = false)
    val originalFilename: String? = null,

    @field:Schema(description = "File size in bytes (available after upload completes)", required = false)
    val fileSizeBytes: Long? = null,

    @field:Schema(description = "When the asset was created", example = "2026-06-20T10:00:00Z")
    val createdAt: String,

    @field:Schema(description = "Temporary URL for previewing image assets", required = false)
    val previewUrl: String? = null,

    @field:Schema(description = "Temporary signed URL for downloading/streaming the asset content", required = false)
    val downloadUrl: String? = null,
)

@Schema(description = "Paginated list of media assets")
data class MediaAssetListResponse(
    @field:Schema(description = "List of assets in this page")
    val assets: List<MediaAssetResponse>,

    @field:Schema(description = "Opaque cursor for the next page, null if no more pages")
    val nextCursor: String?,
)

@Schema(description = "Standard error response envelope")
data class MediaErrorResponse(
    @field:Schema(description = "Machine-readable error code", example = "ASSET_NOT_READY")
    val errorCode: String,

    @field:Schema(description = "Human-readable error message")
    val message: String,

    @field:Schema(description = "Additional error context", required = false)
    val details: Map<String, Any>? = null,
)
