package com.profiletailors.smp.media.infrastructure.http

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

// ─── CAS PUT Request/Response ────────────────────────────────────────────────

@Schema(description = "Request to register a media asset with CAS dedup (PUT)")
data class PutAssetRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[a-f0-9]{64}$")
    @field:Schema(description = "SHA-256 hex of the file content (lowercase, 64 chars)", example = "a1b2c3d4e5f6...")
    val fileHash: String,

    @field:Positive
    @field:Schema(description = "File size in bytes", example = "1234567")
    val fileSizeBytes: Long,

    @field:NotBlank
    @field:Schema(description = "MIME type declared by the client", example = "image/jpeg")
    val declaredMediaType: String,

    @field:Schema(description = "Original filename. Required for OOXML formats.", required = false)
    val originalFilename: String? = null,
)

@Schema(description = "Response from POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload")
data class UploadAssetResponse(
    @field:Schema(description = "Asset identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    val assetId: String,

    @field:Schema(description = "Workspace identifier", example = "ws-123")
    val workspaceId: String,

    @field:Schema(description = "Lifecycle status", example = "READY")
    val status: String,

    @field:Schema(description = "Media type", example = "image/jpeg")
    val mediaType: String,

    @field:Schema(description = "Server-detected media type", example = "image/jpeg")
    val detectedMediaType: String,

    @field:Schema(description = "True if this was a dedup hit during upload", example = "false")
    val deduped: Boolean,

    @field:Schema(description = "Final file size in bytes", example = "1234567")
    val fileSizeBytes: Long,

    @field:Schema(description = "When the asset was created", example = "2026-06-24T10:00:00Z")
    val createdAt: String,
)

@Schema(description = "Response from DELETE /api/workspaces/{workspaceId}/media/assets/{assetId}")
data class DeleteAssetResponse(
    @field:Schema(description = "True if the asset was deleted", example = "true")
    val deleted: Boolean,

    @field:Schema(description = "True if the blob was scheduled for GC", example = "false")
    val blobScheduledForGC: Boolean,
)

// ─── Error response ─────────────────────────────────────────────────────────

@Schema(description = "Standard media error response")
data class MediaErrorResponse(
    @field:Schema(description = "Machine-readable error code", example = "VALIDATION_ERROR")
    val errorCode: String,

    @field:Schema(description = "Human-readable error message")
    val message: String,

    @field:Schema(description = "Additional error context", required = false)
    val details: Map<String, Any>? = null,

    @field:Schema(description = "Existing file hash when ASSET_HASH_MISMATCH", required = false)
    val existingFileHash: String? = null,

    @field:Schema(description = "Seconds to wait before retry", required = false)
    val retryAfterSeconds: Int? = null,
)

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

    @field:Schema(description = "External provider identifier", required = false)
    val sourceProvider: String? = null,

    @field:Schema(description = "Provider-side stable asset identifier", required = false)
    val externalId: String? = null,

    @field:Schema(description = "Canonical source URL in the external provider", required = false)
    val sourceUrl: String? = null,

    @field:Schema(description = "Display name of the credited creator", required = false)
    val authorName: String? = null,

    @field:Schema(description = "URL for the credited creator", required = false)
    val authorUrl: String? = null,

    @field:Schema(description = "Provider-specific metadata", required = false)
    val metadata: Map<String, Any>? = null,
)

@Schema(description = "Paginated list of media assets")
data class MediaAssetListResponse(
    @field:Schema(description = "List of assets in this page")
    val assets: List<MediaAssetResponse>,

    @field:Schema(description = "Opaque cursor for the next page, null if no more pages")
    val nextCursor: String?,
)
