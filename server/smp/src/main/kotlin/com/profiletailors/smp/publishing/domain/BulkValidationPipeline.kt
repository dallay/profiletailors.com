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
        val headerColumns = parseCsvLine(headerLine).map { it.trim() }
        val canonical = BulkTemplate.canonicalHeader().split(",")
        val headerMatches =
            headerColumns.size == canonical.size &&
                headerColumns.map { it.lowercase() } == canonical.map { it.lowercase() }
        if (!headerMatches) {
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
        val headerIndex = canonical.associateWith { col ->
            headerColumns.indexOfFirst { it.equals(col, ignoreCase = true) }
        }
        val bodyIdx = headerIndex["bodyText"] ?: 0
        val scheduledIdx = headerIndex["scheduledFor"] ?: 1
        val mediaIdx = headerIndex["media_urls"] ?: 3

        val seenHashes = mutableSetOf<String>()
        val rows = mutableListOf<BulkRowValidation>()
        var dataRowIndex = 0
        for (rawLine in lines.drop(1)) {
            if (rawLine.isBlank()) continue
            val columns = parseCsvLine(rawLine)
            val padded = if (columns.size <
                canonical.size
            ) {
                columns + List(canonical.size - columns.size) { "" }
            } else {
                columns
            }
            val bodyText = padded.getOrNull(bodyIdx)?.trim()
            val scheduledForRaw = padded.getOrNull(scheduledIdx)?.trim()
            val mediaUrlsRaw = padded.getOrNull(mediaIdx)?.trim()
            val isBlankRow = (
                bodyText.isNullOrBlank() &&
                    scheduledForRaw.isNullOrBlank() &&
                    mediaUrlsRaw.isNullOrBlank()
                )
            if (isBlankRow) continue
            val errors = mutableListOf<ImportError>()
            var scheduledFor: Instant? = null
            if (scheduledForRaw.isNullOrBlank()) {
                errors.add(ImportError(code = "INVALID_DATE", message = "scheduledFor is required"))
            } else {
                try {
                    scheduledFor = Instant.parse(scheduledForRaw)
                    val earliestAllowed = clock.instant().plus(MIN_SCHEDULE_OFFSET)
                    if (scheduledFor.isBefore(earliestAllowed)) {
                        errors.add(ImportError(code = "INVALID_DATE", message = "scheduledFor must be in the future"))
                        scheduledFor = null
                    }
                } catch (_: Exception) {
                    errors.add(ImportError(code = "INVALID_DATE", message = "scheduledFor must be ISO-8601"))
                }
            }
            val mediaUrls = mediaUrlsRaw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val hasBody = !bodyText.isNullOrBlank()
            val hasMedia = mediaUrls.isNotEmpty()
            if (!hasBody && !hasMedia) {
                errors.add(ImportError(code = "MISSING_CONTENT", message = "bodyText or media_urls is required"))
            }
            for (url in mediaUrls) {
                val blockedReason = ssrfBlockReason(url)
                if (blockedReason != null) {
                    errors.add(ImportError(code = "INVALID_MEDIA", message = blockedReason))
                    break
                }
            }
            val dedupKey = computeDedupHash(workspaceId, bodyText ?: "", scheduledForRaw ?: "")
            if (!seenHashes.add(dedupKey)) {
                errors.add(ImportError(code = "DUPLICATE", message = "duplicate row"))
            }
            if (hasMedia && errors.none { it.code == "INVALID_MEDIA" }) {
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
                    bodyText = bodyText?.takeIf { it.isNotBlank() },
                    assetIds = assets.map { it.id },
                    scheduledFor = scheduledFor,
                )
                try {
                    providerCapabilityValidator.validate(
                        ProviderCapabilityValidationInput(
                            provider = validationAccount.provider,
                            socialAccount = validationAccount,
                            publication = draft,
                            assets = assets,
                        ),
                    )
                } catch (ex: IllegalArgumentException) {
                    errors.add(
                        ImportError(code = "CAPABILITY_VIOLATION", message = ex.message ?: "capability violation"),
                    )
                }
            }
            val hasInvalid = errors.any { it.code in INVALID_ROW_CODES }
            val status = if (hasInvalid) BulkRowStatus.INVALID else BulkRowStatus.VALID
            rows.add(
                BulkRowValidation(
                    rowIndex = dataRowIndex,
                    status = status,
                    errors = errors,
                    bodyText = bodyText,
                    scheduledFor = scheduledFor,
                    mediaUrls = mediaUrls,
                ),
            )
            dataRowIndex++
        }
        val conflictIndexes = detectConflictIndexes(workspaceId, rows)
        val flagged = rows.map { r ->
            if (r.rowIndex in conflictIndexes) r.copy(hasConflict = true) else r
        }
        return BulkValidationResult(rows = flagged)
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
