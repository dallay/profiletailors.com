package com.profiletailors.smp.mcp.adapter

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.ListProviderCatalogQuery
import com.profiletailors.smp.publishing.application.ProviderCatalogResponse
import com.profiletailors.smp.publishing.domain.ProviderCatalogItem
import com.profiletailors.smp.publishing.domain.ProviderCatalogState
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ProviderToolsAdapterTest {

    private val mediator: Mediator = mockk()
    private val errorMapper = McpErrorMapper()
    private val adapter = ProviderToolsAdapter(mediator, errorMapper)

    @Test
    fun `list_providers delegates to mediator`() = runTest {
        val providers = listOf(
            ProviderCatalogItem(
                provider = SocialProvider.LINKEDIN,
                accountKinds = setOf("PERSONAL_PROFILE"),
                state = ProviderCatalogState.AVAILABLE,
                reason = null,
                channelLimit = null,
                connectedChannelCount = 1,
                canConnectMore = true,
            ),
        )
        coEvery { mediator.send(ListProviderCatalogQuery) } returns
            ProviderCatalogResponse(providers = providers)

        val result = adapter.listProviders()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.data).isNotNull
        coVerify { mediator.send(ListProviderCatalogQuery) }
    }

    @Test
    fun `list_providers returns error on mediator failure`() = runTest {
        coEvery { mediator.send(ListProviderCatalogQuery) } throws
            RuntimeException("provider error")

        val result = adapter.listProviders()

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("internal")
    }
}
