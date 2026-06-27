package com.profiletailors.smp.media.domain

/**
 * Factory for generating canonical and temporary storage keys for CAS media assets.
 *
 * Key formats:
 * - Canonical (READY blob): `assets/{workspaceId}/blobs/{sha256}.{ext}`
 *   Extension is derived from the **detected** MIME type after magic-byte validation.
 * - Temporary (upload in progress): `assets/{workspaceId}/temp/{assetId}.{ext}`
 *   Extension is derived from the **declared** MIME type sent by the client.
 *
 * The distinction is intentional: the declared type is what the client promises to upload,
 * while the detected type is what the server actually receives. The canonical key reflects
 * the ground truth (detected), while the temp key reflects the client's claim.
 */
object MediaStorageKeys {

    /**
     * Maps MIME types to their file extension (including the leading dot).
     */
    private val MIME_TO_EXTENSION = mapOf(
        "image/jpeg" to ".jpg",
        "image/png" to ".png",
        "image/gif" to ".gif",
        "image/webp" to ".webp",
        "video/mp4" to ".mp4",
        "application/pdf" to ".pdf",
        "application/msword" to ".doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to ".docx",
        "application/vnd.ms-powerpoint" to ".ppt",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to ".pptx",
    )

    /**
     * Generate the canonical storage key for a READY blob.
     *
     * Uses the **detected** media type to derive the extension, ensuring the key
     * reflects what the file actually is, not what the client claimed.
     *
     * @param workspaceId The workspace identifier.
     * @param fileHash Lowercase 64-char SHA-256 hex string.
     * @param detectedMediaType The MIME type detected by the server (e.g. "image/jpeg").
     * @return Canonical key, e.g. `assets/ws_abc/blobs/sha256...abc123.jpg`
     */
    fun canonicalKey(workspaceId: String, fileHash: String, detectedMediaType: String): String {
        require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(fileHash.matches(Regex("^[a-f0-9]{64}$"))) { "fileHash must be lowercase 64-char SHA-256" }
        require(detectedMediaType.isNotBlank()) { "detectedMediaType must not be blank" }
        val ext = parseMediaTypeExtension(detectedMediaType)
        return "assets/$workspaceId/blobs/$fileHash$ext"
    }

    /**
     * Generate the temporary upload key for an asset.
     *
     * Uses the **declared** media type to derive the extension. The client declares
     * the type at PUT time, and this key is used for the temp upload. After magic-byte
     * detection the final canonical key may differ.
     *
     * @param workspaceId The workspace identifier.
     * @param assetId The asset UUID.
     * @param declaredMediaType The MIME type declared by the client (e.g. "image/png").
     * @return Temp key, e.g. `assets/ws_abc/temp/550e8400-....png`
     */
    fun tempKey(workspaceId: String, assetId: String, declaredMediaType: String): String {
        require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(assetId.isNotBlank()) { "assetId must not be blank" }
        val ext = parseMediaTypeExtension(declaredMediaType)
        return "assets/$workspaceId/temp/$assetId$ext"
    }

    /**
     * Map a MIME type to its canonical file extension string.
     *
     * Returns ".bin" as a fallback for unknown types — callers should validate
     * against SUPPORTED_MEDIA_TYPES before calling this.
     *
     * @param mediaType A MIME type string (e.g. "image/jpeg").
     * @return Extension with leading dot, e.g. ".jpg".
     */
    fun parseMediaTypeExtension(mediaType: String): String = MIME_TO_EXTENSION[mediaType] ?: ".bin"
}
