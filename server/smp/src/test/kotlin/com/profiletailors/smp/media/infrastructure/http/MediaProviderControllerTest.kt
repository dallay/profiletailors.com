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
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.media.application.ImportExternalAssetCommand
import com.profiletailors.smp.media.application.ImportExternalAssetResult
import com.profiletailors.smp.media.application.port.ImportProviderAssetQuery
import com.profiletailors.smp.media.application.port.ProviderExternalAsset
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.application.port.ProviderPageMeta
import com.profiletailors.smp.media.application.port.ProviderSearchItem
import com.profiletailors.smp.media.application.port.ProviderSearchPage
import com.profiletailors.smp.media.application.port.SearchProviderPhotosQuery
import com.profiletailors.smp.media.application.port.UnsupportedProviderException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class MediaProviderControllerTest {
    private val workspaceId = "workspace-1"

    @Test
    fun `search maps provider page and dispatches qualified query`() = runTest {
        val mediator = ProviderMediator(searchResult = searchPage())
        val response = controller(mediator).searchProviderPhotos(workspaceId, "mountains", 2)

        assertEquals("unsplash:photo-1", response.items.single().externalId)
        assertEquals(2, response.page.number)
        val sent = mediator.sentQueries.single() as SearchProviderPhotosQuery
        assertEquals("unsplash", sent.providerId)
        assertEquals(workspaceId, sent.workspaceId)
        assertEquals("mountains", sent.query)
    }

    @Test
    fun `disabled provider maps search to 404`() = runTest {
        val exception = assertThrows<ResponseStatusException> {
            controller(ProviderMediator(queryFailure = UnsupportedProviderException("unsplash")))
                .searchProviderPhotos(workspaceId, "mountains", 1)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `workspace mismatch maps search to 403 before mediator`() = runTest {
        val exception = assertThrows<ResponseStatusException> {
            controller(ProviderMediator(), contextWorkspaceId = "other-workspace")
                .searchProviderPhotos(workspaceId, "mountains", 1)
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun `unverified principal rejects search before mediator`() = runTest {
        val exception = assertThrows<FeatureEmailVerificationRequired> {
            controller(
                ProviderMediator(),
                emailVerificationPolicy = EmailVerificationPolicy { true },
            ).searchProviderPhotos(workspaceId, "mountains", 1)
        }
        assertEquals(AuthFeature.UPLOAD_MEDIA, exception.feature)
    }

    @Test
    fun `invalid external id maps import to 400 before provider lookup`() = runTest {
        val exception = assertThrows<ResponseStatusException> {
            controller(ProviderMediator()).importProviderPhoto(
                workspaceId,
                ProviderImportRequest("pexels:photo-1"),
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.reason.orEmpty().contains("INVALID_EXTERNAL_ID"))
    }

    @Test
    fun `missing photo id maps import to 400`() = runTest {
        val exception = assertThrows<ResponseStatusException> {
            controller(ProviderMediator()).importProviderPhoto(
                workspaceId,
                ProviderImportRequest("unsplash:"),
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `disabled provider maps import to 404`() = runTest {
        val exception = assertThrows<ResponseStatusException> {
            controller(ProviderMediator(queryFailure = UnsupportedProviderException("unsplash")))
                .importProviderPhoto(workspaceId, ProviderImportRequest("unsplash:photo-1"))
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `import maps new asset result`() = runTest {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            importResult = ImportExternalAssetResult("asset-1", workspaceId, false, "image/jpeg", 4L),
        )
        val response = controller(mediator).importProviderPhoto(
            workspaceId,
            ProviderImportRequest("unsplash:photo-1"),
        )

        assertEquals("asset-1", response.assetId)
        assertFalse(response.deduped)
        val query = mediator.sentQueries.single() as ImportProviderAssetQuery
        assertEquals("unsplash:photo-1", query.externalId)
        val command = mediator.lastCommand as ImportExternalAssetCommand
        assertEquals(workspaceId, command.workspaceId)
    }

    @Test
    fun `import maps canonical dedup result`() = runTest {
        val mediator = ProviderMediator(
            importedAsset = externalAsset(),
            importResult = ImportExternalAssetResult("asset-canonical", workspaceId, true, "image/jpeg", 4L),
        )
        val response = controller(mediator).importProviderPhoto(
            workspaceId,
            ProviderImportRequest("unsplash:photo-1"),
        )

        assertEquals("asset-canonical", response.assetId)
        assertTrue(response.deduped)
    }

    private fun controller(
        mediator: Mediator,
        contextWorkspaceId: String = workspaceId,
        emailVerificationPolicy: EmailVerificationPolicy = EmailVerificationPolicy { false },
    ): MediaProviderController = MediaProviderController(
        mediator = mediator,
        resourceContextProvider = FixedResourceContextProvider(contextWorkspaceId),
        principalContextProvider = FixedPrincipalContextProvider(),
        principalIdentityLookup = NoOpPrincipalIdentityLookup(),
        emailVerificationPolicy = emailVerificationPolicy,
    )

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
        externalId = ProviderExternalId("unsplash:photo-1"),
        mediaType = "image/jpeg",
        contentLength = 4L,
        bytes = flowOf(byteArrayOf(1, 2, 3, 4)),
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
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "user@example.test",
            authenticationMethod = "TEST",
        )
    }

    private class ProviderMediator(
        private val searchResult: ProviderSearchPage? = null,
        private val importedAsset: ProviderExternalAsset? = null,
        private val importResult: ImportExternalAssetResult? = null,
        private val queryFailure: RuntimeException? = null,
    ) : Mediator {
        val sentQueries = mutableListOf<Any>()
        var lastCommand: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            queryFailure?.let { throw it }
            sentQueries += query
            return when (query) {
                is SearchProviderPhotosQuery -> requireNotNull(searchResult)
                is ImportProviderAssetQuery -> requireNotNull(importedAsset)
                else -> error("Unexpected query: ${query::class.simpleName}")
            } as TResponse
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Unexpected command: ${command::class.simpleName}")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastCommand = command
            return requireNotNull(importResult) as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) = Unit

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) = Unit
    }
}
