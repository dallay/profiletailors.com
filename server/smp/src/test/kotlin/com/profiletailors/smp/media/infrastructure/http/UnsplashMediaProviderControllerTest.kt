package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.media.application.ImportUnsplashPhotoCommand
import com.profiletailors.smp.media.application.MediaAssetSummary
import com.profiletailors.smp.media.application.SearchUnsplashPhotosQuery
import com.profiletailors.smp.media.application.UnsplashPhoto
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UnsplashMediaProviderControllerTest {

    @Test
    fun `search dispatches SearchUnsplashPhotosQuery and maps response`() = runTest {
        val mediator = CapturingMediator(
            queryResult = listOf(
                UnsplashPhoto(
                    externalId = "photo-1",
                    name = "Workspace inspiration",
                    previewUrl = "https://images.unsplash.com/photo-1?w=400",
                    importUrl = "https://images.unsplash.com/photo-1?w=1600",
                    sourceUrl = "https://unsplash.com/photos/photo-1",
                    authorName = "Jane Creator",
                    authorUrl = "https://unsplash.com/@jane",
                    downloadLocation = "https://api.unsplash.com/photos/photo-1/download",
                ),
            ),
        )
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
        )

        val response = controller.search("remote work")

        val sent = mediator.lastQuery as SearchUnsplashPhotosQuery
        assertEquals("remote work", sent.query)
        assertEquals(1, response.photos.size)
        assertEquals("photo-1", response.photos.single().externalId)
        assertEquals("Workspace inspiration", response.photos.single().name)
        assertEquals("https://images.unsplash.com/photo-1?w=400", response.photos.single().previewUrl)
        assertEquals("https://unsplash.com/photos/photo-1", response.photos.single().sourceUrl)
        assertEquals("Jane Creator", response.photos.single().authorName)
        assertEquals("https://unsplash.com/@jane", response.photos.single().authorUrl)
    }

    @Test
    fun `import requires workspace context and dispatches ImportUnsplashPhotoCommand`() = runTest {
        val mediator = CapturingMediator(
            commandResult = MediaAssetSummary(
                assetId = "asset-1",
                workspaceId = "workspace-1",
                mediaType = "image/jpeg",
                sourceType = "EXTERNAL",
                status = "READY",
                originalFilename = "photo-1.jpg",
                fileSizeBytes = 1024,
                fileHash = "hash-1",
                createdAt = "2026-07-15T10:00:00Z",
                previewUrl = "/api/media/assets/asset-1/preview",
                sourceProvider = "unsplash",
                externalId = "photo-1",
                sourceUrl = "https://unsplash.com/photos/photo-1",
                authorName = "Jane Creator",
                authorUrl = "https://unsplash.com/@jane",
            ),
        )
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
        )

        val response = controller.import("photo-1")

        val sent = mediator.lastCommand as ImportUnsplashPhotoCommand
        assertEquals("workspace-1", sent.workspaceId)
        assertEquals("photo-1", sent.externalId)
        assertEquals("asset-1", response.assetId)
        assertEquals("EXTERNAL", response.sourceType)
        assertEquals("unsplash", response.sourceProvider)
        assertEquals("photo-1", response.externalId)
    }

    @Test
    fun `import fails before dispatch when resource context is missing`() = runTest {
        val mediator = FailingMediator()
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = MissingResourceContextProvider,
        )

        assertThrows<MissingResourceContextException> {
            controller.import("photo-1")
        }
    }

    @Test
    fun `import fails before dispatch when active workspace id is missing`() = runTest {
        val mediator = FailingMediator()
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider(null),
        )

        assertThrows<WorkspaceOwnershipOperationRequiresWorkspaceContextException> {
            controller.import("photo-1")
        }
    }

    private class CapturingMediator(private val queryResult: Any? = null, private val commandResult: Any? = null) :
        Mediator {
        var lastQuery: Any? = null
        var lastCommand: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            return queryResult as TResponse
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            lastCommand = command
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastCommand = command
            return commandResult as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }

    private class FailingMediator : Mediator {
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            error("Unexpected query: ${query::class.simpleName}")
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Unexpected command: ${command::class.simpleName}")
        }

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            error("Unexpected command: ${command::class.simpleName}")
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }

    private class FixedResourceContextProvider(private val workspaceId: String?) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private object MissingResourceContextProvider : ResourceContextProvider {
        override fun current(): ResourceContext? = null
    }
}
