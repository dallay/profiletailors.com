package com.profiletailors.smp.governance.application

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class MediaAssetStatusReaderTest {

    private val reader: MediaAssetStatusReader = FakeMediaAssetStatusReader(
        mapOf(
            "ws-1" to "asset-ready" to "READY",
            "ws-1" to "asset-suspended" to "SUSPENDED",
        ),
    )

    @Test
    fun `returns ready and suspended status as strings`() = runTest {
        reader.readStatus("ws-1", "asset-ready") shouldBe "READY"
        reader.readStatus("ws-1", "asset-suspended") shouldBe "SUSPENDED"
        reader.readStatus("ws-1", "asset-ready").shouldBeInstanceOf<String>()
    }

    @Test
    fun `returns null when the asset is missing`() = runTest {
        reader.readStatus("ws-1", "missing-asset") shouldBe null
        reader.readStatus("ws-missing", "asset-ready") shouldBe null
    }

    private class FakeMediaAssetStatusReader(private val statuses: Map<Pair<String, String>, String>) :
        MediaAssetStatusReader {
        override suspend fun readStatus(workspaceId: String, assetId: String): String? =
            statuses[workspaceId to assetId]
    }
}
