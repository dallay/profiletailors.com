package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.OwnerId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GetLinkHandlerTest {
    private val workspaceId = "0199b1ca-0000-7000-8000-000000000001"
    private val resourceContextProvider = mockk<ResourceContextProvider>()
    private val linkRepository = mockk<LinkRepository>()

    @Test
    fun `does not access a link outside the active workspace`() = runBlocking {
        every { resourceContextProvider.require() } returns ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
        val linkId = UUID.fromString("0199b1ca-0000-7000-8000-000000000002")
        coEvery {
            linkRepository.findById(LinkId(linkId), OwnerId.from(workspaceId))
        } returns null
        val handler = GetLinkHandler(
            resourceContextProvider,
            linkRepository,
            mockk<ShortLinksConfigProperties> {
                every { shortUrlBase } returns "https://short.example"
                every { publicHost } returns "short.example"
            },
        )

        try {
            handler.handle(GetLinkQuery(linkId))
            throw AssertionError("Expected link not found")
        } catch (_: LinkNotFoundApplicationException) {
            coVerify(exactly = 1) {
                linkRepository.findById(LinkId(linkId), OwnerId.from(workspaceId))
            }
        }
    }
}
