package com.profiletailors.smp.shortlinks.application

import com.profiletailors.smp.shortlinks.domain.LinkClickRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RecordLinkClickCommandHandlerTest {
    private val clickRecorder = mockk<LinkClickRepository>(relaxed = true)
    private val handler = RecordLinkClickCommandHandler(clickRecorder)

    @Test
    fun `records click through domain port`() = runTest {
        val linkId = UUID.fromString("0199b1ca-0000-7000-8000-000000000001")

        handler.handle(RecordLinkClickCommand(linkId))

        coVerify(exactly = 1) { clickRecorder.record(LinkId(linkId)) }
    }
}
