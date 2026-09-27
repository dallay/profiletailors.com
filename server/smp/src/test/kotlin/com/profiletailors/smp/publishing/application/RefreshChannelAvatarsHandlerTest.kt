package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.LinkedInAvatarFetcher
import com.profiletailors.smp.publishing.domain.ReconnectReason
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RefreshChannelAvatarsHandlerTest {

    private val now = Instant.parse("2026-09-26T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `refreshes stale avatar and publishes channel event`() = runTest {
        val account = linkedInAccount(id = "acc-1", avatarUrl = "https://media.licdn.com/stale.jpg")
        val fixture = Fixture(accounts = listOf(account))
        fixture.pictures["token-1"] = "https://media.licdn.com/fresh.jpg"

        val result = fixture.handler().handle(RefreshChannelAvatarsCommand())

        assertEquals(listOf("acc-1"), result.refreshedAccountIds)
        assertTrue(result.skippedAccountIds.isEmpty())
        assertTrue(result.failedAccountIds.isEmpty())
        assertEquals("https://media.licdn.com/fresh.jpg", fixture.accounts.upserted?.avatarUrl)
        assertEquals(1, fixture.events.size)
        assertEquals(ChannelEventType.CONNECTED_CHANNEL_UPDATED, fixture.events.single().type)
        assertEquals("acc-1", fixture.events.single().socialAccountId)
    }

    @Test
    fun `skips account when avatar is unchanged`() = runTest {
        val account = linkedInAccount(id = "acc-1", avatarUrl = "https://media.licdn.com/same.jpg")
        val fixture = Fixture(accounts = listOf(account))
        fixture.pictures["token-1"] = "https://media.licdn.com/same.jpg"

        val result = fixture.handler().handle(RefreshChannelAvatarsCommand())

        assertEquals(listOf("acc-1"), result.skippedAccountIds)
        assertTrue(result.refreshedAccountIds.isEmpty())
        assertEquals(null, fixture.accounts.upserted)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `skips account when reconnect is required`() = runTest {
        val account = linkedInAccount(id = "acc-1", avatarUrl = "https://media.licdn.com/stale.jpg")
        val fixture = Fixture(accounts = listOf(account))
        fixture.reconnects += "acc-1"

        val result = fixture.handler().handle(RefreshChannelAvatarsCommand())

        assertEquals(listOf("acc-1"), result.skippedAccountIds)
        assertTrue(result.refreshedAccountIds.isEmpty())
        assertEquals(null, fixture.accounts.upserted)
    }

    @Test
    fun `skips account when provider returns no picture`() = runTest {
        val account = linkedInAccount(id = "acc-1", avatarUrl = "https://media.licdn.com/stale.jpg")
        val fixture = Fixture(accounts = listOf(account))

        val result = fixture.handler().handle(RefreshChannelAvatarsCommand())

        assertEquals(listOf("acc-1"), result.skippedAccountIds)
        assertTrue(result.refreshedAccountIds.isEmpty())
        assertEquals(null, fixture.accounts.upserted)
    }

    @Test
    fun `continues with next account after reconnect failure`() = runTest {
        val stale = linkedInAccount(id = "acc-1", avatarUrl = "https://media.licdn.com/stale.jpg")
        val fresh = linkedInAccount(id = "acc-2", avatarUrl = "https://media.licdn.com/old.jpg")
        val fixture = Fixture(accounts = listOf(stale, fresh))
        fixture.reconnects += "acc-1"
        fixture.pictures["token-1"] = "https://media.licdn.com/new.jpg"

        val result = fixture.handler().handle(RefreshChannelAvatarsCommand())

        assertEquals(listOf("acc-2"), result.refreshedAccountIds)
        assertEquals(listOf("acc-1"), result.skippedAccountIds)
        assertTrue(result.failedAccountIds.isEmpty())
    }

    @Test
    fun `ignores non-linkedin and inactive accounts`() = runTest {
        val fixture = Fixture(accounts = emptyList())

        val result = fixture.handler().handle(RefreshChannelAvatarsCommand())

        assertTrue(result.refreshedAccountIds.isEmpty())
        assertTrue(result.skippedAccountIds.isEmpty())
        assertTrue(result.failedAccountIds.isEmpty())
        assertTrue(fixture.events.isEmpty())
    }

    private fun linkedInAccount(id: String, avatarUrl: String?): SocialAccount = SocialAccount(
        id = id,
        socialConnectionId = "conn-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "person-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Yuniel Acosta",
        profileUrn = "urn:li:person:person-1",
        avatarUrl = avatarUrl,
        status = SocialConnectionStatus.ACTIVE,
    )

    private inner class Fixture(accounts: List<SocialAccount>) {
        val accounts = RecordingAccountRepository(accounts.associateBy { it.id }.toMutableMap())
        val pictures = mutableMapOf<String, String?>()
        val reconnects = mutableSetOf<String>()
        val events = mutableListOf<ChannelEvent>()
        private val resolver = RefreshAwareCredentialResolver { account ->
            if (account.id in reconnects) {
                throw ReconnectRequiredException("reconnect", ReconnectReason.REFRESH_UNAVAILABLE)
            }
            "token-1"
        }
        private val fetcher = LinkedInAvatarFetcher { pictures[it] }
        private val publisher = ChannelEventPublisher { events += it }
        private val contexts = object : ResourceContextProvider {
            override fun current(): ResourceContext? = ResourceContext(ResourceContextType.WORKSPACE, "workspace-1")
        }

        fun handler(): RefreshChannelAvatarsHandler = RefreshChannelAvatarsHandler(
            resourceContextProvider = contexts,
            socialAccountRepository = accounts,
            credentialResolver = resolver,
            avatarFetcher = fetcher,
            channelEventPublisher = publisher,
            clock = clock,
        )
    }

    private class RecordingAccountRepository(private val items: MutableMap<String, SocialAccount>) :
        SocialAccountRepository {
        var upserted: SocialAccount? = null
        override suspend fun upsert(account: SocialAccount): SocialAccount {
            upserted = account
            items[account.id] = account
            return account
        }
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? =
            items[accountId]?.takeIf { it.workspaceId == workspaceId }
        override suspend fun findFirstActiveByWorkspace(workspaceId: String): SocialAccount? =
            items.values.firstOrNull { it.workspaceId == workspaceId }
        override suspend fun listActiveByWorkspace(workspaceId: String): List<SocialAccount> =
            items.values.filter { it.workspaceId == workspaceId && it.status == SocialConnectionStatus.ACTIVE }
    }
}
