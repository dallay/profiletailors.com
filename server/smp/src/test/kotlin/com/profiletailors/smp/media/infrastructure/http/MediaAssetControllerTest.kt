package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.media.application.CreateUploadedAssetCommand
import com.profiletailors.smp.media.application.CreateUploadedAssetResult
import com.profiletailors.smp.media.application.GetWorkspaceAssetQuery
import com.profiletailors.smp.media.application.ListWorkspaceAssetsQuery
import com.profiletailors.smp.media.application.ListWorkspaceAssetsResult
import com.profiletailors.smp.media.application.MediaAssetSummary
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.server.ResponseStatusException

class MediaAssetControllerTest {

    private val workspaceId = "workspace-1"

    @Test
    fun `createAsset dispatches CreateUploadedAssetCommand and returns response`() = runTest {
        val mediator = CapturingMediator(
            commandResult = CreateUploadedAssetResult(
                assetId = "asset-1",
                workspaceId = workspaceId,
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/jpeg",
                status = "PROCESSING",
            ),
        )
        val controller = controller(mediator)

        val response = controller.createAsset(
            CreateMediaAssetRequest(
                sourceType = "UPLOADED",
                mediaType = "image/jpeg",
                originalFilename = null,
            ),
        )

        assertEquals("asset-1", response.assetId)
        assertEquals(workspaceId, response.workspaceId)
        assertEquals("UPLOADED", response.sourceType)
        assertEquals("PROCESSING", response.status)
        assertNull(response.originalFilename)
        assertNull(response.fileSizeBytes)
        assertNotNull(response.createdAt)

        val sent = mediator.lastCommand as CreateUploadedAssetCommand
        assertEquals(workspaceId, sent.workspaceId)
        assertEquals(MediaSourceType.UPLOADED, sent.sourceType)
        assertEquals("image/jpeg", sent.mediaType)
        assertNull(sent.originalFilename)
    }

    @Test
    fun `createAsset normalizes lowercase source type`() = runTest {
        val mediator = CapturingMediator(
            commandResult = CreateUploadedAssetResult(
                assetId = "asset-2",
                workspaceId = workspaceId,
                sourceType = MediaSourceType.UPLOADED,
                mediaType = "image/png",
                status = "PROCESSING",
            ),
        )
        val controller = controller(mediator)

        val response = controller.createAsset(
            CreateMediaAssetRequest(
                sourceType = "uploaded",
                mediaType = "image/png",
                originalFilename = null,
            ),
        )

        assertEquals("asset-2", response.assetId)
        assertEquals("PROCESSING", response.status)
    }

    @Test
    fun `createAsset passes through non uploaded enum source types to handler`() = runTest {
        val mediator = CapturingMediator(
            commandResult = CreateUploadedAssetResult(
                assetId = "asset-external",
                workspaceId = workspaceId,
                sourceType = MediaSourceType.EXTERNAL,
                mediaType = "image/jpeg",
                status = "PROCESSING",
            ),
        )
        val controller = controller(mediator)

        controller.createAsset(
            CreateMediaAssetRequest(
                sourceType = "EXTERNAL",
                mediaType = "image/jpeg",
            ),
        )

        val sent = mediator.lastCommand as CreateUploadedAssetCommand
        assertEquals(MediaSourceType.EXTERNAL, sent.sourceType)
    }

    @Test
    fun `createAsset preserves email verification required denial`() = runTest {
        val controller = controller(
            ThrowingMediator(FeatureEmailVerificationRequired(AuthFeature.UPLOAD_MEDIA)),
        )

        val exception = assertThrows<FeatureEmailVerificationRequired> {
            controller.createAsset(
                CreateMediaAssetRequest(
                    sourceType = "UPLOADED",
                    mediaType = "image/jpeg",
                    originalFilename = null,
                ),
            )
        }

        assertEquals(AuthFeature.UPLOAD_MEDIA, exception.feature)
    }

    @Test
    fun `listAssets defaults to READY status and page size 50`() = runTest {
        val mediator = CapturingMediator(
            queryResult = ListWorkspaceAssetsResult(
                assets = listOf(
                    MediaAssetSummary(
                        assetId = "asset-1",
                        workspaceId = workspaceId,
                        mediaType = "image/jpeg",
                        sourceType = "UPLOADED",
                        status = "READY",
                        originalFilename = "photo.jpg",
                        fileSizeBytes = 1024L,
                        fileHash = null,
                        createdAt = "2026-06-20T10:00:00Z",
                    ),
                ),
                nextCursor = null,
            ),
        )
        val controller = controller(mediator)

        val response = controller.listAssets()

        assertEquals(1, response.assets.size)
        assertEquals("asset-1", response.assets.single().assetId)
        assertNull(response.nextCursor)

        val sent = mediator.lastQuery as ListWorkspaceAssetsQuery
        assertEquals(workspaceId, sent.workspaceId)
        assertEquals(setOf(MediaAssetStatus.READY), sent.statuses)
        assertEquals(50, sent.pageSize)
        assertNull(sent.cursor)
    }

    @Test
    fun `listAssets parses comma separated statuses and passes cursor`() = runTest {
        val mediator = CapturingMediator(
            queryResult = ListWorkspaceAssetsResult(emptyList(), nextCursor = "next-cursor"),
        )
        val controller = controller(mediator)

        val response = controller.listAssets(
            status = "READY, PROCESSING",
            pageSize = 25,
            cursor = "cursor-1",
        )

        assertEquals("next-cursor", response.nextCursor)

        val sent = mediator.lastQuery as ListWorkspaceAssetsQuery
        assertEquals(setOf(MediaAssetStatus.READY, MediaAssetStatus.PROCESSING), sent.statuses)
        assertEquals(25, sent.pageSize)
        assertEquals("cursor-1", sent.cursor)
    }

    @Test
    fun `listAssets falls back to READY when statuses are blank or invalid`() = runTest {
        val mediator = CapturingMediator(
            queryResult = ListWorkspaceAssetsResult(emptyList(), nextCursor = null),
        )
        val controller = controller(mediator)

        controller.listAssets(status = "GARBAGE, ,INVALID")

        val sent = mediator.lastQuery as ListWorkspaceAssetsQuery
        assertEquals(setOf(MediaAssetStatus.READY), sent.statuses)
    }

    @Test
    fun `listAssets caps page size at max`() = runTest {
        val mediator = CapturingMediator(
            queryResult = ListWorkspaceAssetsResult(emptyList(), nextCursor = null),
        )
        val controller = controller(mediator)

        controller.listAssets(pageSize = 999)

        val sent = mediator.lastQuery as ListWorkspaceAssetsQuery
        assertEquals(MediaAssetController.MAX_PAGE_SIZE, sent.pageSize)
    }

    @Test
    fun `getAsset dispatches GetWorkspaceAssetQuery and maps response`() = runTest {
        val mediator = CapturingMediator(
            queryResult = MediaAssetSummary(
                assetId = "asset-1",
                workspaceId = workspaceId,
                mediaType = "image/jpeg",
                sourceType = "UPLOADED",
                status = "READY",
                originalFilename = "photo.jpg",
                fileSizeBytes = 2048L,
                fileHash = null,
                createdAt = "2026-06-20T12:00:00Z",
            ),
        )
        val controller = controller(mediator)

        val response = controller.getAsset("asset-1")

        assertEquals("asset-1", response.assetId)
        assertEquals(workspaceId, response.workspaceId)
        assertEquals("READY", response.status)
        assertEquals("photo.jpg", response.originalFilename)
        assertEquals(2048L, response.fileSizeBytes)

        val sent = mediator.lastQuery as GetWorkspaceAssetQuery
        assertEquals("asset-1", sent.assetId)
        assertEquals(workspaceId, sent.workspaceId)
    }

    @Test
    fun `uploadAsset rejects oversized multipart files with CONTENT_TOO_LARGE`() = runTest {
        val filePart = mockk<FilePart>()
        every { filePart.headers() } returns HttpHeaders().apply {
            contentLength = MediaAssetController.MAX_FILE_SIZE_BYTES + 1
        }
        val controller = controller(FailingMediator())

        val exception = assertThrows<ResponseStatusException> {
            controller.uploadAsset("asset-1", filePart)
        }

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, exception.statusCode)
    }

    private fun controller(mediator: Mediator): MediaAssetController = MediaAssetController(
        mediator = mediator,
        resourceContextProvider = FixedResourceContextProvider(workspaceId),
    )

    private class CapturingMediator(
        private val commandResult: CreateUploadedAssetResult? = null,
        private val queryResult: Any? = null,
    ) : Mediator {
        var lastCommand: Any? = null
        var lastQuery: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            return queryResult as TResponse
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used in this test")
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

    private class ThrowingMediator(private val exception: RuntimeException) : Mediator {
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = throw exception

        override suspend fun <TCommand : Command> send(command: TCommand): Unit = throw exception

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult =
            throw exception

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

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }
}
