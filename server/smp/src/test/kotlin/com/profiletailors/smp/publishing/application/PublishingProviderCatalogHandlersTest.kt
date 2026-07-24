package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderCatalogAvailability
import com.profiletailors.smp.publishing.domain.ProviderCatalogConnectionCounter
import com.profiletailors.smp.publishing.domain.ProviderCatalogItem
import com.profiletailors.smp.publishing.domain.ProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderCatalogState
import com.profiletailors.smp.publishing.domain.ProviderConnectionNotAvailableException
import com.profiletailors.smp.publishing.domain.ProviderLockReason
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceCapacityPolicy
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceEntitlementPolicy
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublishingProviderCatalogHandlersTest {
    @Test
    fun `hides provider before evaluating entitlement or capacity`() = runTest {
        val policy = DefaultProviderCatalogPolicy(
            availability = ProviderCatalogAvailability { false },
            entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> false },
            capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> false },
            connectionCounter = ProviderCatalogConnectionCounter { _, _ -> 3 },
        )

        val result = policy.evaluate(SocialProvider.LINKEDIN, "workspace-1")

        assertEquals(ProviderCatalogState.HIDDEN, result.state)
        assertNull(result.reason)
        assertEquals(3, result.connectedChannelCount)
        assertFalse(result.canConnectMore)
    }

    @Test
    fun `resolves permissive LinkedIn personal profile catalog entry`() = runTest {
        val policy = DefaultProviderCatalogPolicy(
            availability = ProviderCatalogAvailability { true },
            entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> true },
            capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> true },
            connectionCounter = ProviderCatalogConnectionCounter { _, _ -> 1 },
        )

        val result = policy.evaluate(SocialProvider.LINKEDIN, "workspace-1")

        assertEquals(ProviderCatalogState.AVAILABLE, result.state)
        assertNull(result.reason)
        assertEquals(setOf("PERSONAL_PROFILE"), result.accountKinds)
        assertNull(result.channelLimit)
        assertEquals(1, result.connectedChannelCount)
        assertTrue(result.canConnectMore)
    }

    @Test
    fun `returns typed policy lock reasons while preserving existing channel count`() = runTest {
        val notEntitled = DefaultProviderCatalogPolicy(
            availability = ProviderCatalogAvailability { true },
            entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> false },
            capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> true },
            connectionCounter = ProviderCatalogConnectionCounter { _, _ -> 2 },
        ).evaluate(SocialProvider.LINKEDIN, "workspace-1")
        val capacityReached = DefaultProviderCatalogPolicy(
            availability = ProviderCatalogAvailability { true },
            entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> true },
            capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> false },
            connectionCounter = ProviderCatalogConnectionCounter { _, _ -> 2 },
        ).evaluate(SocialProvider.LINKEDIN, "workspace-1")

        assertEquals(ProviderCatalogState.LOCKED, notEntitled.state)
        assertEquals(ProviderLockReason.NOT_ENTITLED, notEntitled.reason)
        assertEquals(ProviderCatalogState.LOCKED, capacityReached.state)
        assertEquals(ProviderLockReason.CAPACITY_REACHED, capacityReached.reason)
        assertEquals(2, capacityReached.connectedChannelCount)
        assertFalse(capacityReached.canConnectMore)
    }

    @Test
    fun `oauth initiation re-evaluates a now locked provider before generating state`() = runTest {
        val handler = InitiateLinkedInConnectionHandler(
            principalContextProvider = principalContextProvider(),
            resourceContextProvider = workspaceContextProvider(),
            oauthStateSigner = object : OAuthStateSigner {
                override fun sign(payload: LinkedInOAuthStatePayload): String = "signed-state"

                override fun verify(state: String): LinkedInOAuthStatePayload = error("Not used by initiation")
            },
            authorizationUrlBuilder = configuredAuthorizationUrlBuilder(),
            clock = Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC),
            providerCatalogPolicy = DefaultProviderCatalogPolicy(
                availability = ProviderCatalogAvailability { true },
                entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> true },
                capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> false },
                connectionCounter = ProviderCatalogConnectionCounter { _, _ -> 1 },
            ),
        )

        assertThrows(ProviderConnectionNotAvailableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(InitiateLinkedInConnectionCommand("https://app.example.com/callback"))
            }
        }
    }

    @Test
    fun `catalog response omits hidden providers`() = runTest {
        val handler = ListProviderCatalogHandler(
            resourceContextProvider = workspaceContextProvider(),
            providerCatalogPolicy = ProviderCatalogPolicy { provider, _ ->
                ProviderCatalogItem(
                    provider = provider,
                    accountKinds = emptySet(),
                    state = ProviderCatalogState.HIDDEN,
                    reason = null,
                    channelLimit = null,
                    connectedChannelCount = 0,
                    canConnectMore = true,
                )
            },
        )

        val result = handler.handle(ListProviderCatalogQuery)

        assertTrue(result.providers.isEmpty())
    }

    private fun principalContextProvider(): PrincipalContextProvider = object : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "principal-1",
        )
    }

    private fun workspaceContextProvider(): ResourceContextProvider = object : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
    }

    private fun configuredAuthorizationUrlBuilder(): LinkedInAuthorizationUrlBuilder =
        object : LinkedInAuthorizationUrlBuilder {
            override fun buildAuthorizationUrl(state: String, redirectUri: String): String =
                "https://linkedin.example/authorize?state=$state"

            override fun isConfigured(): Boolean = true
        }
}
