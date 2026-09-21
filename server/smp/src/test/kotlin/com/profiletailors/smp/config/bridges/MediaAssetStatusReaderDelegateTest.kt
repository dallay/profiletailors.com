package com.profiletailors.smp.config.bridges

import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class MediaAssetStatusReaderDelegateTest {

    private val mediaAssetRepository: MediaAssetRepository = mockk()
    private val reader = MediaAssetStatusReaderDelegate(mediaAssetRepository)

    @Test
    fun `returns ready and suspended status names`() = runTest {
        coEvery { mediaAssetRepository.findByWorkspaceAndId("ws-1", "asset-ready") } returns
            asset(MediaAssetStatus.READY)
        coEvery { mediaAssetRepository.findByWorkspaceAndId("ws-1", "asset-suspended") } returns
            asset(MediaAssetStatus.SUSPENDED)

        reader.readStatus("ws-1", "asset-ready") shouldBe "READY"
        reader.readStatus("ws-1", "asset-suspended") shouldBe "SUSPENDED"
    }

    @Test
    fun `returns null when the media asset is missing`() = runTest {
        coEvery { mediaAssetRepository.findByWorkspaceAndId("ws-1", "missing") } returns null

        reader.readStatus("ws-1", "missing") shouldBe null
    }

    private fun asset(status: MediaAssetStatus): MediaAsset {
        val mediaAsset = mockk<MediaAsset>()
        every { mediaAsset.status } returns status
        return mediaAsset
    }
}
