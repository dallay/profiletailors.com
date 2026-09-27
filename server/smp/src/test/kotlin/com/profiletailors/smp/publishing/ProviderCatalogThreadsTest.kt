package com.profiletailors.smp.publishing

import com.profiletailors.smp.publishing.application.DefaultProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderCatalogAvailability
import com.profiletailors.smp.publishing.domain.ProviderCatalogConnectionCounter
import com.profiletailors.smp.publishing.domain.ProviderCatalogState
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceCapacityPolicy
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceEntitlementPolicy
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProviderCatalogThreadsTest {
    @Test
    fun `disabled threads is hidden`() = runTest {
        val policy = policy(available = false)

        policy.evaluate(SocialProvider.THREADS, "workspace-1").state shouldBe ProviderCatalogState.HIDDEN
    }

    @Test
    fun `valid threads configuration is available`() = runTest {
        val policy = policy(available = true)

        policy.evaluate(SocialProvider.THREADS, "workspace-1").let {
            assertEquals(ProviderCatalogState.AVAILABLE, it.state)
            assertEquals(setOf("PERSONAL_PROFILE"), it.accountKinds)
        }
    }

    private fun policy(available: Boolean) = DefaultProviderCatalogPolicy(
        availability = ProviderCatalogAvailability { available },
        entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> true },
        capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> true },
        connectionCounter = ProviderCatalogConnectionCounter { _, _ -> 0 },
    )
}

private infix fun ProviderCatalogState.shouldBe(expected: ProviderCatalogState) {
    assertEquals(expected, this)
}
