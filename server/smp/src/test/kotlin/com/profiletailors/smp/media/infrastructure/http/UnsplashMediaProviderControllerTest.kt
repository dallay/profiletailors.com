package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.media.application.ImportUnsplashPhotoCommand
import com.profiletailors.smp.media.application.MediaAssetSummary
import com.profiletailors.smp.media.application.SearchUnsplashPhotosQuery
import com.profiletailors.smp.media.application.UnsplashPhoto
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UnsplashMediaProviderControllerTest {

    @Test
    fun `search dispatches SearchUnsplashPhotosQuery and maps response`() = runTest {
        val photo = UnsplashPhoto(
            externalId = "photo-1",
            name = "Workspace inspiration",
            previewUrl = "https://images.unsplash.com/photo-1?w=400",
            importUrl = "https://images.unsplash.com/photo-1?w=1600",
            sourceUrl = "https://unsplash.com/photos/photo-1",
            authorName = "Jane Creator",
            authorUrl = "https://unsplash.com/@jane",
            downloadLocation = "https://api.unsplash.com/photos/photo-1/download",
        )
        val mediator = mockk<Mediator>()
        coEvery { mediator.send(any<SearchUnsplashPhotosQuery>()) } returns listOf(photo)
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
        )

        val response = controller.search("remote work")

        coVerify { mediator.send(match<SearchUnsplashPhotosQuery> { it.query == "remote work" }) }
        response.photos.size shouldBe 1
        response.photos.single().externalId shouldBe "photo-1"
        response.photos.single().name shouldBe "Workspace inspiration"
        response.photos.single().previewUrl shouldBe "https://images.unsplash.com/photo-1?w=400"
        response.photos.single().sourceUrl shouldBe "https://unsplash.com/photos/photo-1"
        response.photos.single().authorName shouldBe "Jane Creator"
        response.photos.single().authorUrl shouldBe "https://unsplash.com/@jane"
    }

    @Test
    fun `import requires workspace context and dispatches ImportUnsplashPhotoCommand`() = runTest {
        val summary = MediaAssetSummary(
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
        )
        val mediator = mockk<Mediator>()
        coEvery { mediator.send(any<ImportUnsplashPhotoCommand>()) } returns summary
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
        )

        val response = controller.import("photo-1")

        coVerify {
            mediator.send(
                match<ImportUnsplashPhotoCommand> {
                    it.workspaceId == "workspace-1" && it.externalId == "photo-1"
                },
            )
        }
        response.assetId shouldBe "asset-1"
        response.sourceType shouldBe "EXTERNAL"
        response.sourceProvider shouldBe "unsplash"
        response.externalId shouldBe "photo-1"
    }

    @Test
    fun `import fails before dispatch when resource context is missing`() = runTest {
        val mediator = mockk<Mediator>(relaxed = true)
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = MissingResourceContextProvider,
        )

        shouldThrow<MissingResourceContextException> {
            controller.import("photo-1")
        }
        coVerify(exactly = 0) { mediator.send(any<ImportUnsplashPhotoCommand>()) }
    }

    @Test
    fun `import fails before dispatch when active workspace id is missing`() = runTest {
        val mediator = mockk<Mediator>(relaxed = true)
        val controller = UnsplashMediaProviderController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider(null),
        )

        shouldThrow<WorkspaceOwnershipOperationRequiresWorkspaceContextException> {
            controller.import("photo-1")
        }
        coVerify(exactly = 0) { mediator.send(any<ImportUnsplashPhotoCommand>()) }
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
