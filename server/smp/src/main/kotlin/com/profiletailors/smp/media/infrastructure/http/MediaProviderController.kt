package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.permissivePrincipalContextProvider
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.media.application.ImportExternalAssetCommand
import com.profiletailors.smp.media.application.ImportExternalAssetResult
import com.profiletailors.smp.media.application.port.ImportProviderAssetQuery
import com.profiletailors.smp.media.application.port.ProviderSearchItem
import com.profiletailors.smp.media.application.port.SearchProviderPhotosQuery
import com.profiletailors.smp.media.application.port.UnsupportedProviderException
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * HTTP boundary for media provider (Unsplash) operations.
 *
 * The controller is the single seam between the React SPA composer and the
 * `MediaProvider` port: it dispatches [SearchProviderPhotosQuery] for the
 * `/search` endpoint and the [ImportExternalAssetCommand] for the `/import`
 * endpoint. Verified-email and rate-limit enforcement are split between this
 * controller (email) and [com.profiletailors.smp.media.application.ImportExternalAssetHandler]
 * (rate-limit / concurrent-slot) per the spec.
 *
 * Feature-flag behavior:
 * - When the provider is disabled at startup (`mediaprovider.unsplash.enabled=false`)
 *   the [com.profiletailors.smp.media.application.port.MediaProvider] bean is NOT
 *   registered, so the search handler throws [UnsupportedProviderException] and
 *   the import handler throws via the same seam. Both map to 404 in this
 *   controller.
 *
 * External-id validation:
 * - The frontend MUST send `unsplash:<photoId>`. Anything else (`pexx:abc`,
 *   `abc123` with no prefix, etc.) is rejected with 400 `INVALID_EXTERNAL_ID`
 *   BEFORE the provider is contacted.
 */
@Validated
@RestController
@RequestMapping(value = ["/api/workspaces/{workspaceId}/media/providers/unsplash"])
@Tag(
    name = "Media Provider — Unsplash",
    description = "Search and import stock photos from the Unsplash provider",
)
class MediaProviderController(
    private val mediator: Mediator,
    private val resourceContextProvider: ResourceContextProvider,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) {
    private val logger = LoggerFactory.getLogger(MediaProviderController::class.java)

    @Operation(
        summary = "Search Unsplash for stock photos",
        description = "Forwarded to the Unsplash adapter behind a feature flag. Returns 404 when the " +
            "provider is disabled.",
        responses = [
            ApiResponse(responseCode = "200", description = "Search results returned"),
            ApiResponse(responseCode = "404", description = "Provider disabled or unsupported"),
            ApiResponse(responseCode = "403", description = "Email verification required"),
        ],
    )
    @GetMapping(value = ["/search"])
    suspend fun searchProviderPhotos(
        @Parameter(description = "Workspace identifier", required = true)
        @PathVariable workspaceId: String,
        @Parameter(description = "Search query", required = true)
        @RequestParam
        @NotBlank query: String,
        @Parameter(description = "Page number (1-based)", example = "1")
        @RequestParam(defaultValue = "1")
        @Min(MIN_PAGE_NUMBER.toLong())
        @Max(MAX_PAGE_NUMBER.toLong())
        page: Int = DEFAULT_PAGE_NUMBER,
    ): ProviderSearchResponse {
        val resolvedWorkspaceId = resolveWorkspaceId(workspaceId)
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )

        val pageResult = try {
            mediator.send(
                SearchProviderPhotosQuery(
                    providerId = EXTERNAL_ID_PROVIDER,
                    workspaceId = resolvedWorkspaceId,
                    query = query,
                    page = page,
                ),
            )
        } catch (e: UnsupportedProviderException) {
            // No Unsplash adapter registered → feature flag is off.
            // The exception's message is intentionally surfaced verbatim in the 404 body.
            @Suppress("SwallowedException")
            val notFound = ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
            throw notFound
        }

        return ProviderSearchResponse(
            items = pageResult.items.map { it.toResponse() },
            page = ProviderSearchPageResponse(
                number = pageResult.page.number,
                size = pageResult.page.size,
                total = pageResult.page.total,
            ),
        )
    }

    @Operation(
        summary = "Import a stock photo into the workspace media library",
        description = "Imports the bytes through the same CAS pipeline used by uploads, persists " +
            "attribution, and returns either a newly-created asset row or the canonical existing " +
            "asset row when the same bytes have been imported before (dedup hit).",
        responses = [
            ApiResponse(responseCode = "200", description = "Asset created or canonical asset returned"),
            ApiResponse(
                responseCode = "400",
                description = "externalId is missing the 'unsplash:' prefix or uses a different provider",
            ),
            ApiResponse(responseCode = "404", description = "Provider disabled or unsupported"),
            ApiResponse(responseCode = "403", description = "Email verification required"),
            ApiResponse(responseCode = "429", description = "Rate limit or concurrent-slot exceeded"),
            ApiResponse(responseCode = "502", description = "Provider error"),
            ApiResponse(responseCode = "504", description = "Provider unreachable"),
        ],
    )
    @PostMapping(value = ["/import"], consumes = ["application/json"])
    suspend fun importProviderPhoto(
        @Parameter(description = "Workspace identifier", required = true)
        @PathVariable workspaceId: String,
        @Valid @RequestBody request: ProviderImportRequest,
    ): ProviderImportResponse {
        val resolvedWorkspaceId = resolveWorkspaceId(workspaceId)
        validateUnsplashExternalId(request.externalId)

        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )

        // First fetch the provider asset, then dispatch the import command. If the provider
        // is not registered the lookup below will surface `UnsupportedProviderException`.
        val externalAsset = try {
            mediator.send(
                ImportProviderAssetQuery(
                    providerId = EXTERNAL_ID_PROVIDER,
                    workspaceId = resolvedWorkspaceId,
                    externalId = request.externalId,
                ),
            )
        } catch (e: UnsupportedProviderException) {
            // Provider is disabled → 404. Message surfaced verbatim.
            @Suppress("SwallowedException")
            val notFound = ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
            throw notFound
        }

        val result: ImportExternalAssetResult = try {
            mediator.send(
                ImportExternalAssetCommand(
                    workspaceId = resolvedWorkspaceId,
                    externalAsset = externalAsset,
                ),
            )
        } catch (e: UnsupportedProviderException) {
            // Provider is disabled → 404. Message surfaced verbatim.
            @Suppress("SwallowedException")
            val notFound = ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
            throw notFound
        }

        logger.info(
            "media.provider.import.completed workspaceId={} externalId={} assetId={} deduped={}",
            resolvedWorkspaceId,
            request.externalId,
            result.assetId,
            result.deduped,
        )

        return ProviderImportResponse(
            assetId = result.assetId,
            workspaceId = result.workspaceId,
            deduped = result.deduped,
            mediaType = result.mediaType,
            fileSizeBytes = result.fileSizeBytes,
        )
    }

    private fun resolveWorkspaceId(requestedWorkspaceId: String): String {
        val context = resourceContextProvider.requireWorkspaceContext()
        val resolvedWorkspaceId = requireNotNull(context.workspaceId) {
            "Workspace context does not include a workspaceId"
        }
        if (resolvedWorkspaceId != requestedWorkspaceId) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Workspace context does not match the requested path",
            )
        }
        return resolvedWorkspaceId
    }

    private fun validateUnsplashExternalId(externalId: String) {
        if (!externalId.startsWith(UNSPLASH_PREFIX)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EXTERNAL_ID: externalId must start with '$UNSPLASH_PREFIX'",
            )
        }
        if (externalId.length <= UNSPLASH_PREFIX.length) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EXTERNAL_ID: externalId is missing the photoId segment",
            )
        }
    }

    companion object {
        const val UNSPLASH_PREFIX = "unsplash:"
        private const val EXTERNAL_ID_PROVIDER = "unsplash"
        private const val DEFAULT_PAGE_NUMBER = 1
        private const val MIN_PAGE_NUMBER = 1
        private const val MAX_PAGE_NUMBER = 50
    }
}

private fun ProviderSearchItem.toResponse(): ProviderSearchItemResponse = ProviderSearchItemResponse(
    externalId = externalId.value,
    previewUrl = previewUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
    authorName = authorName,
    authorUrl = authorUrl,
    sourceUrl = sourceUrl,
)
