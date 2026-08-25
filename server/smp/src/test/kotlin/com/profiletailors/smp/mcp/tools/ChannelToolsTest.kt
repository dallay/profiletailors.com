package com.profiletailors.smp.mcp.tools

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.ConnectedChannelsResponse
import com.profiletailors.smp.publishing.application.ConnectedSocialChannelSummary
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("fast")
class ChannelToolsTest {

    private val mediator: Mediator = mockk()
    private val errorMapper = McpErrorMapper()
    private val adapter = ChannelTools(mediator, errorMapper)

    @Test
    fun `list_channels delegates to mediator and returns channel list`() = runTest {
        val channels = listOf(
            ConnectedSocialChannelSummary(
                socialAccountId = "sa-1",
                connectionId = "conn-1",
                provider = SocialProvider.LINKEDIN,
                accountKind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Test Account",
                status = SocialConnectionStatus.ACTIVE,
                avatarUrl = "https://example.com/avatar.png",
                connectedAt = Instant.now(),
                lastSyncedAt = Instant.now(),
            ),
        )
        coEvery { mediator.send(any<ListConnectedChannelsQuery>()) } returns
            ConnectedChannelsResponse(channels = channels)

        val result = adapter.listChannels(status = null)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.data).isNotNull
        coVerify { mediator.send(any<ListConnectedChannelsQuery>()) }
    }

    @Test
    fun `list_channels filters by status when provided`() = runTest {
        coEvery { mediator.send(any<ListConnectedChannelsQuery>()) } returns
            ConnectedChannelsResponse(channels = emptyList())

        adapter.listChannels(status = "ACTIVE")

        coVerify {
            mediator.send(
                match<ListConnectedChannelsQuery> {
                    it.status == SocialConnectionStatus.ACTIVE
                },
            )
        }
    }

    @Test
    fun `list_channels returns error on mediator failure`() = runTest {
        coEvery { mediator.send(any<ListConnectedChannelsQuery>()) } throws
            RuntimeException("db error")

        val result = adapter.listChannels(status = null)

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("internal")
    }
}
