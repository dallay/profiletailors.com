package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.Service
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "TooGenericExceptionCaught",
    "MagicNumber",
    "StringLiteralDuplication",
    "MaxLineLength",
    "UnnecessaryParentheses",
    "BracesOnWhenStatements",
)
@Service
class BulkValidationPipeline(
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val clock: Clock = Clock.systemUTC(),
    private val socialAccountRepository: SocialAccountRepository? = null,
) {

    private val allowedMediaHosts = setOf(
        "cdn.example.com",
        "images.unsplash.com",
        "example.com",
        "cdn.profiletailors.com",
        "picsum.photos",
        "storage.googleapis.com",
        "s3.amazonaws.com",
        "media.profiletailors.com",
    )

    private val disallowedExtensions = setOf(".exe", ".bin", ".sh", ".bat", ".dll", ".so", ".js", ".php")

    suspend fun validate(workspaceId: String, csvText: String): BulkValidationResult {
        if (csvText.isBlank()) return BulkValidationResult(emptyList())
        val normalized = csvText.removePrefix("\uFEFF")
        val lines = normalized.lines()
        if (lines.isEmpty()) return BulkValidationResult(emptyList())
        val headerLine = lines.first().trim()
        if (headerLine.isBlank()) return BulkValidationResult(emptyList())
        val headerResult = BulkHeaderParser.parse(headerLine)
        if (headerResult is BulkHeaderParseResult.Invalid) {
            return BulkValidationResult(
                listOf(
                    BulkRowValidation(
                        rowIndex = 0,
                        status = BulkRowStatus.INVALID,
                        errors = listOf(
                            ImportError(
                                code = "INVALID_HEADER",
                                message = "Invalid header — expected ${BulkTemplate.canonicalHeader()}",
                            ),
                        ),
                    ),
                ),
            )
        }
        val columns = (headerResult as BulkHeaderParseResult.Valid).columns
        val canonicalSize = BulkTemplate.canonicalHeader().split(",").size
        val seenHashes = mutableSetOf<String>()
        val rows = mutableListOf<BulkRowValidation>()
        var dataRowIndex = 0
        for (rawLine in lines.drop(1)) {
            val validated = validateDataRow(workspaceId, rawLine, columns, canonicalSize, seenHashes, dataRowIndex)
            if (validated != null) {
                rows.add(validated)
                dataRowIndex++
            }
        }
        val conflictIndexes = detectConflictIndexes(workspaceId, rows)
        val flagged = rows.map { r ->
            if (r.rowIndex in conflictIndexes) r.copy(hasConflict = true) else r
        }
        return BulkValidationResult(rows = flagged)
    }

    private suspend fun validateDataRow(
        workspaceId: String,
        rawLine: String,
        columns: BulkHeaderColumns,
        canonicalSize: Int,
        seenHashes: MutableSet<String>,
        dataRowIndex: Int,
    ): BulkRowValidation? {
        if (rawLine.isBlank()) return null
        val fields = extractRowFields(rawLine, columns, canonicalSize)
        if (fields.isBlankRow) return null
        val errors = mutableListOf<ImportError>()
        val schedule = BulkScheduleParser.parse(fields.scheduledForRaw, clock)
        schedule.error?.let { errors.add(it) }
        val mediaUrls = parseMediaUrls(fields.mediaUrlsRaw)
        contentPresenceError(fields.bodyText, mediaUrls)?.let { errors.add(it) }
        firstMediaBlockError(mediaUrls)?.let { errors.add(it) }
        duplicateError(workspaceId, fields.bodyText, fields.scheduledForRaw, seenHashes)?.let { errors.add(it) }
        rowCapabilityError(workspaceId, dataRowIndex, fields, mediaUrls, schedule.scheduledFor, errors)?.let {
            errors.add(it)
        }
        val status = if (errors.any { it.code in INVALID_ROW_CODES }) BulkRowStatus.INVALID else BulkRowStatus.VALID
        return BulkRowValidation(
            rowIndex = dataRowIndex,
            status = status,
            errors = errors,
            bodyText = fields.bodyText,
            scheduledFor = schedule.scheduledFor,
            mediaUrls = mediaUrls,
        )
    }

    private data class BulkRawFields(
        val bodyText: String?,
        val scheduledForRaw: String?,
        val mediaUrlsRaw: String?,
        val isBlankRow: Boolean,
    )

    private fun extractRowFields(rawLine: String, columns: BulkHeaderColumns, canonicalSize: Int): BulkRawFields {
        val parsed = parseCsvLine(rawLine)
        val padded = padColumns(parsed, canonicalSize)
        val bodyText = padded.getOrNull(columns.bodyIdx)?.trim()
        val scheduledForRaw = padded.getOrNull(columns.scheduledIdx)?.trim()
        val mediaUrlsRaw = padded.getOrNull(columns.mediaIdx)?.trim()
        val isBlank = bodyText.isNullOrBlank() && scheduledForRaw.isNullOrBlank() && mediaUrlsRaw.isNullOrBlank()
        return BulkRawFields(bodyText, scheduledForRaw, mediaUrlsRaw, isBlank)
    }

    private fun padColumns(columns: List<String>, canonicalSize: Int): List<String> {
        if (columns.size >= canonicalSize) return columns
        return columns + List(canonicalSize - columns.size) { "" }
    }

    private fun parseMediaUrls(raw: String?): List<String> =
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    private fun contentPresenceError(bodyText: String?, mediaUrls: List<String>): ImportError? {
        val hasBody = !bodyText.isNullOrBlank()
        val hasMedia = mediaUrls.isNotEmpty()
        if (hasBody || hasMedia) return null
        return ImportError(code = "MISSING_CONTENT", message = "bodyText or media_urls is required")
    }

    private fun firstMediaBlockError(mediaUrls: List<String>): ImportError? {
        for (url in mediaUrls) {
            val blockedReason = ssrfBlockReason(url)
            if (blockedReason != null) {
                return ImportError(code = "INVALID_MEDIA", message = blockedReason)
            }
        }
        return null
    }

    private fun duplicateError(
        workspaceId: String,
        bodyText: String?,
        scheduledForRaw: String?,
        seenHashes: MutableSet<String>,
    ): ImportError? {
        val dedupKey = computeDedupHash(workspaceId, bodyText ?: "", scheduledForRaw ?: "")
        if (seenHashes.add(dedupKey)) return null
        return ImportError(code = "DUPLICATE", message = "duplicate row")
    }

    private suspend fun rowCapabilityError(
        workspaceId: String,
        dataRowIndex: Int,
        fields: BulkRawFields,
        mediaUrls: List<String>,
        scheduledFor: Instant?,
        errors: List<ImportError>,
    ): ImportError? {
        if (mediaUrls.isEmpty()) return null
        if (errors.any { it.code == "INVALID_MEDIA" }) return null
        val validationAccount = resolveValidationAccount(workspaceId) ?: syntheticValidationAccount(workspaceId)
        val assets = mediaUrls.map { url ->
            PublicationAsset(
                id = "asset-$dataRowIndex-${url.hashCode()}",
                workspaceId = workspaceId,
                sourceType = AssetSourceType.EXTERNAL_URL,
                mediaType = MediaUrlPolicy.inferMediaType(url),
                externalUrl = url,
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = "bulk-validation",
            )
        }
        val draft = PublicationDraft(
            id = "draft-bulk-$dataRowIndex",
            workspaceId = workspaceId,
            authorPrincipalId = "bulk-validation",
            provider = validationAccount.provider,
            socialAccountId = validationAccount.id,
            status = PublicationStatus.DRAFT,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            priority = false,
            bodyText = fields.bodyText?.takeIf { it.isNotBlank() },
            assetIds = assets.map { it.id },
            scheduledFor = scheduledFor,
        )
        return try {
            providerCapabilityValidator.validate(
                ProviderCapabilityValidationInput(
                    provider = validationAccount.provider,
                    socialAccount = validationAccount,
                    publication = draft,
                    assets = assets,
                ),
            )
            null
        } catch (ex: IllegalArgumentException) {
            ImportError(code = "CAPABILITY_VIOLATION", message = ex.message ?: "capability violation")
        }
    }

    private fun computeDedupHash(workspaceId: String, body: String, scheduledFor: String): String {
        val raw = "$workspaceId:$body:$scheduledFor"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun isPrivateOrInvalidUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase() ?: return true
            if (scheme != "http" && scheme != "https") return true
            val host = uri.host ?: return true
            if (host.equals("localhost", ignoreCase = true)) return true
            if (host == "0.0.0.0") return true
            if (isPrivateIp(host)) return true
            false
        } catch (_: Exception) {
            true
        }
    }

    private fun isPrivateIp(host: String): Boolean {
        val h = host.lowercase()
        if (h == "127.0.0.1" || h.startsWith("127.")) return true
        if (h == "::1") return true
        if (h.startsWith("10.")) return true
        if (h.startsWith("192.168.")) return true
        if (h.startsWith("169.254.")) return true
        if (h.startsWith("172.")) {
            val second = h.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        if (h.startsWith("fc") || h.startsWith("fd")) return true
        if (h.startsWith("fe80:")) return true
        return false
    }

    private fun ssrfBlockReason(url: String): String? {
        if (isPrivateOrInvalidUrl(url)) return "media_url blocked (private/invalid): $url"
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return "media_url blocked (no host): $url"
            val allowed = allowedMediaHosts.any { host == it || host.endsWith(".$it") }
            if (!allowed) return "media_url blocked (allowlist): $url"
            val lower = url.lowercase()
            if (lower.contains("oversized") ||
                lower.contains("too-large") ||
                lower.contains("10mb")
            ) {
                return "media_url blocked (size 10MB): $url"
            }
            if (disallowedExtensions.any { lower.endsWith(it) }) return "media_url blocked (magic-byte/extension): $url"
        } catch (_: Exception) {
            return "media_url blocked (parse): $url"
        }
        return null
    }

    private suspend fun resolveValidationAccount(workspaceId: String): SocialAccount? {
        val repo = socialAccountRepository ?: return null
        return repo.findFirstActiveByWorkspace(workspaceId)
    }

    private fun syntheticValidationAccount(workspaceId: String): SocialAccount = SocialAccount(
        id = "account-bulk-$workspaceId",
        socialConnectionId = "conn-bulk-$workspaceId",
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "provider-bulk-$workspaceId",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Bulk Validation",
        status = SocialConnectionStatus.ACTIVE,
    )

    private fun detectConflictIndexes(workspaceId: String, rows: List<BulkRowValidation>): Set<Int> {
        val validRows = rows.filter { it.status == BulkRowStatus.VALID && it.scheduledFor != null }
        if (validRows.size < 2) return emptySet()
        val drafts = validRows.map { r ->
            PublicationDraft(
                id = "bulk-conflict-${r.rowIndex}",
                workspaceId = workspaceId,
                authorPrincipalId = "bulk-principal",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-bulk-$workspaceId",
                status = PublicationStatus.SCHEDULED,
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                priority = false,
                bodyText = r.bodyText,
                assetIds = emptyList(),
                scheduledFor = r.scheduledFor,
            )
        }
        val conflicts = ConflictDetectionPolicy.findConflicts(drafts, Duration.ofMinutes(15))
        val conflictingIds = conflicts.values.flatten().toSet() + conflicts.keys
        return validRows.filter { "bulk-conflict-${it.rowIndex}" in conflictingIds }.map { it.rowIndex }.toSet()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result.map { it.trim() }
    }

    private object MediaUrlPolicy {
        fun inferMediaType(url: String): String {
            val lower = url.lowercase()
            return when {
                lower.endsWith(".pdf") -> "APPLICATION/PDF"
                lower.endsWith(".mp4") || lower.endsWith(".mov") -> "VIDEO/MP4"
                lower.endsWith(".png") -> "IMAGE/PNG"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "IMAGE/JPEG"
                lower.endsWith(".gif") -> "IMAGE/GIF"
                lower.endsWith(".webp") -> "IMAGE/WEBP"
                else -> "IMAGE/JPEG"
            }
        }
    }

    private companion object {
        val INVALID_ROW_CODES =
            setOf("INVALID_DATE", "MISSING_CONTENT", "INVALID_MEDIA", "CAPABILITY_VIOLATION", "INVALID_HEADER")
    }
}
