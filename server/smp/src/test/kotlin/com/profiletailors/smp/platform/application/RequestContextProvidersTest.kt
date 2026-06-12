package com.profiletailors.smp.platform.application

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.platform.infrastructure.InMemoryRequestContextStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RequestContextProvidersTest {

    @Test
    fun `principal provider requires an existing principal context`() {
        val store = InMemoryRequestContextStore()
        val provider = StoreBackedPrincipalContextProvider(store)

        val error = assertThrows(MissingPrincipalContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                provider.require()
            }
        }

        assertEquals("Authenticated principal context is required.", error.message)
    }

    @Test
    fun `principal provider returns stored principal context`() {
        val store = InMemoryRequestContextStore()
        val provider = StoreBackedPrincipalContextProvider(store)
        val principal = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "user-123",
        )

        store.setPrincipalContext(principal)

        assertEquals(principal, kotlinx.coroutines.runBlocking { provider.require() })
    }

    @Test
    fun `resource provider requires an existing resource context`() {
        val store = InMemoryRequestContextStore()
        val provider = StoreBackedResourceContextProvider(store)

        val error = assertThrows(MissingResourceContextException::class.java) {
            provider.require()
        }

        assertEquals("Resolved resource context is required.", error.message)
    }

    @Test
    fun `resource provider returns stored resource context`() {
        val store = InMemoryRequestContextStore()
        val provider = StoreBackedResourceContextProvider(store)
        val context = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )

        store.setResourceContext(context)

        assertEquals(context, provider.require())
    }
}
