package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.infrastructure.http.IdentityProblemDetailsHandler
import com.profiletailors.smp.media.application.ImportExternalAssetCommand
import com.profiletailors.smp.media.application.ImportExternalAssetResult
import com.profiletailors.smp.media.application.RateLimitExceededException
import com.profiletailors.smp.media.application.port.ImportProviderAssetQuery
import com.profiletailors.smp.media.application.port.ProviderExternalAsset
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.application.port.ProviderPageMeta
import com.profiletailors.smp.media.application.port.ProviderSearchItem
import com.profiletailors.smp.media.application.port.ProviderSearchPage
import com.profiletailors.smp.media.application.port.SearchProviderPhotosQuery
import com.profiletailors.smp.media.application.port.UnsupportedProviderException
import com.profiletailors.smp.mediaprovider.unsplash.ProviderErrorException
import com.profiletailors.smp.mediaprovider.unsplash.ProviderImportRejectedException
import com.profiletailors.smp.mediaprovider.unsplash.ProviderUnavailableException
import com.profiletailors.smp.mediaprovider.unsplash.UnsplashErrorMapper
import com.profiletailors.smp.mediaprovider.unsplash.UnsplashRateLimitedException
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class MediaProviderControllerWebFluxTest {

    private val workspaceId = "ws-test-123"

    // ─── Search Endpoint ─────────────────────────────────────────────────────

    @Test
    fun `search returns 200 with formatted JSON when successful`() {
        val mediator = ProviderMediator(searchResult = searchPage())
        client(mediator).get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains&page=2")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.items[0].externalId").isEqualTo("unsplash:photo-1")
            .jsonPath("$.items[0].previewUrl").isEqualTo("https://example.test/preview")
            .jsonPath("$.page.number").isEqualTo(2)
            .jsonPath("$.page.size").isEqualTo(20)
    }

    @Test
    fun `search returns 404 when Unsplash provider is disabled`() {
        val mediator = ProviderMediator(queryFailure = UnsupportedProviderException("unsplash"))
        client(mediator).get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.title").isEqualTo("404")
            .jsonPath("$.detail").isEqualTo("No media provider registered for 'unsplash'")
    }

    @Test
    fun `search returns 403 when email is unverified`() {
        val client = client(ProviderMediator(), emailVerificationPolicy = { true })
        client.get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.title").isEqualTo("Email verification required")
    }

    @Test
    fun `search returns 403 when workspace context mismatch occurs`() {
        val client = client(ProviderMediator(), contextWorkspaceId = "different-workspace")
        client.get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Workspace context does not match the requested path")
    }

    @Test
    fun `search returns 429 with Retry-After when Unsplash rate limit hit`() {
        val mediator = ProviderMediator(queryFailure = UnsplashRateLimitedException(45))
        client(mediator).get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "45")
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("PROVIDER_RATE_LIMITED")
            .jsonPath("$.retryAfterSeconds").isEqualTo(45)
    }

    @Test
    fun `search returns 502 with PROVIDER_ERROR when Unsplash returns 4xx`() {
        val mediator = ProviderMediator(queryFailure = ProviderErrorException("401 unauthorized"))
        client(mediator).get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("PROVIDER_ERROR")
            .jsonPath("$.detail").isEqualTo("Unsplash rejected the request. Please retry shortly.")
    }

    @Test
    fun `search returns 504 with PROVIDER_UNREACHABLE when Unsplash timeout occurs`() {
        val mediator = ProviderMediator(queryFailure = ProviderUnavailableException("timeout"))
        client(mediator).get()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/search?query=mountains")
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("PROVIDER_UNREACHABLE")
            .jsonPath("$.detail").isEqualTo("Unsplash is currently unreachable. Please retry shortly.")
    }

    // ─── Import Endpoint ─────────────────────────────────────────────────────

    @Test
    fun `import returns 200 with active asset details when successful`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            importResult = ImportExternalAssetResult("asset-new-123", workspaceId, false, "image/jpeg", 1024L),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.assetId").isEqualTo("asset-new-123")
            .jsonPath("$.deduped").isEqualTo(false)
            .jsonPath("$.mediaType").isEqualTo("image/jpeg")
            .jsonPath("$.fileSizeBytes").isEqualTo(1024)
    }

    @Test
    fun `import returns 200 with dedup status when bytes were imported before`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            importResult = ImportExternalAssetResult("asset-dup-456", workspaceId, true, "image/png", 512L),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.assetId").isEqualTo("asset-dup-456")
            .jsonPath("$.deduped").isEqualTo(true)
    }

    @Test
    fun `import returns 400 when externalId is missing the unsplash prefix`() {
        client(ProviderMediator()).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "pexels:photo-123"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            // Map fallback handles 400 ResponseStatusException
            .jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("INVALID_EXTERNAL_ID"))
    }

    @Test
    fun `import returns 400 when externalId has empty photo segment`() {
        client(ProviderMediator()).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("INVALID_EXTERNAL_ID"))
    }

    @Test
    fun `import returns 404 when Unsplash provider is disabled`() {
        val mediator = ProviderMediator(queryFailure = UnsupportedProviderException("unsplash"))
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `import returns 403 when email is unverified`() {
        val client = client(ProviderMediator(), emailVerificationPolicy = { true })
        client.post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.title").isEqualTo("Email verification required")
    }

    @Test
    fun `import returns 429 with Retry-After when Unsplash rate limit hit`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            commandFailure = UnsplashRateLimitedException(60),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("PROVIDER_RATE_LIMITED")
            .jsonPath("$.retryAfterSeconds").isEqualTo(60)
    }

    @Test
    fun `import returns 429 with Retry-After when core concurrent upload slot is full`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            commandFailure = RateLimitExceededException(workspaceId, "concurrent_uploads", 5, 5, 10),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "10")
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("RATE_LIMIT_EXCEEDED")
    }

    @Test
    fun `import returns 422 with IMPORT_REJECTED when Unsplash size or MIME checks fail`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            commandFailure = ProviderImportRejectedException("upstream image is video/mp4"),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().value { code -> org.junit.jupiter.api.Assertions.assertEquals(422, code) }
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("IMPORT_REJECTED")
            .jsonPath("$.detail").isEqualTo("Unsplash import rejected: upstream image is video/mp4")
    }

    @Test
    fun `import returns 502 with PROVIDER_ERROR when Unsplash returns 4xx`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            commandFailure = ProviderErrorException("401 Unauthorized from Unsplash due to wrong keys"),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("PROVIDER_ERROR")
            .jsonPath("$.detail").isEqualTo("Unsplash rejected the request. Please retry shortly.")
    }

    @Test
    fun `import returns 504 with PROVIDER_UNREACHABLE when Unsplash timeout occurs`() {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            commandFailure = ProviderUnavailableException("Request connection timeout"),
        )
        client(mediator).post()
            .uri("/api/workspaces/$workspaceId/media/providers/unsplash/import")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProviderImportRequest(externalId = "unsplash:photo-abc"))
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("PROVIDER_UNREACHABLE")
            .jsonPath("$.detail").isEqualTo("Unsplash is currently unreachable. Please retry shortly.")
    }

    // ─── Test Helpers ────────────────────────────────────────────────────────

    private fun client(
        mediator: Mediator,
        contextWorkspaceId: String = workspaceId,
        emailVerificationPolicy: EmailVerificationPolicy = EmailVerificationPolicy { false },
    ): WebTestClient {
        val controller = MediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider(contextWorkspaceId),
            principalContextProvider = FixedPrincipalContextProvider(),
            principalIdentityLookup = NoOpPrincipalIdentityLookup(),
            emailVerificationPolicy = emailVerificationPolicy,
        )
        val mediaAdvice = MediaProblemDetailsHandler(UnsplashErrorMapper())
        val identityAdvice = IdentityProblemDetailsHandler()

        return WebTestClient.bindToController(controller)
            .controllerAdvice(mediaAdvice, identityAdvice)
            .build()
    }

    private fun searchPage(): ProviderSearchPage = ProviderSearchPage(
        items = listOf(
            ProviderSearchItem(
                externalId = ProviderExternalId("unsplash:photo-1"),
                previewUrl = "https://example.test/preview",
                fullUrl = "https://example.test/full",
                width = 1200,
                height = 800,
                authorName = "Author",
                authorUrl = "https://example.test/author",
                sourceUrl = "https://example.test/photo",
            ),
        ),
        page = ProviderPageMeta(number = 2, size = 20, total = 1),
    )

    private fun externalAsset(): ProviderExternalAsset = ProviderExternalAsset(
        externalId = ProviderExternalId("unsplash:photo-abc"),
        mediaType = "image/jpeg",
        contentLength = 1024L,
        bytes = flowOf(byteArrayOf(1, 2, 3)),
        sourceProvider = "unsplash",
        sourceUrl = "https://example.test/photo",
        authorName = "Author",
        authorUrl = "https://example.test/author",
    )

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private class FixedPrincipalContextProvider : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "user@example.test",
            authenticationMethod = "SESSION",
        )
    }

    private class ProviderMediator(
        private val searchResult: ProviderSearchPage? = null,
        private val importedAsset: ProviderExternalAsset? = null,
        private val importResult: ImportExternalAssetResult? = null,
        private val queryFailure: RuntimeException? = null,
        private val commandFailure: RuntimeException? = null,
    ) : Mediator {

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            queryFailure?.let { throw it }
            return when (query) {
                is SearchProviderPhotosQuery -> requireNotNull(searchResult)
                is ImportProviderAssetQuery -> requireNotNull(importedAsset)
                else -> error("Unexpected query: ${query::class.simpleName}")
            } as TResponse
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Unexpected command")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            commandFailure?.let { throw it }
            return when (command) {
                is ImportExternalAssetCommand -> requireNotNull(importResult)
                else -> error("Unexpected command: ${command::class.simpleName}")
            } as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) = Unit

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) = Unit
    }
}
