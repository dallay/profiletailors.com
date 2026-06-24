package com.profiletailors.common.domain.context

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ContextProvidersTest {

    @Test
    fun `principal provider should return null when no principal`() = runTest {
        val provider = object : PrincipalContextProvider {
            override suspend fun current(): PrincipalContext? = null
        }

        assertThat(provider.current()).isNull()
    }

    @Test
    fun `principal provider should throw when require fails`() = runTest {
        val provider = object : PrincipalContextProvider {
            override suspend fun current(): PrincipalContext? = null
        }

        try {
            provider.require()
            throw AssertionError("Expected MissingPrincipalContextException")
        } catch (e: MissingPrincipalContextException) {
            assertThat(e.message).isEqualTo("Authenticated principal context is required.")
        }
    }

    @Test
    fun `principal provider should return context when available`() = runTest {
        val ctx = PrincipalContext(
            principalId = "user-1",
            principalType = PrincipalType.USER,
            subject = "user@example.com",
        )
        val provider = object : PrincipalContextProvider {
            override suspend fun current(): PrincipalContext? = ctx
        }

        assertThat(provider.current()).isSameAs(ctx)
        assertThat(provider.require()).isSameAs(ctx)
    }

    @Test
    fun `resource provider should return null when no context`() {
        val provider = object : ResourceContextProvider {
            override fun current(): ResourceContext? = null
        }

        assertThat(provider.current()).isNull()
    }

    @Test
    fun `resource provider should throw when require fails`() {
        val provider = object : ResourceContextProvider {
            override fun current(): ResourceContext? = null
        }

        assertThatThrownBy { provider.require() }
            .isInstanceOf(MissingResourceContextException::class.java)
            .hasMessage("Resolved resource context is required.")
    }

    @Test
    fun `resource provider should return context when available`() {
        val ctx = ResourceContext(type = ResourceContextType.GLOBAL)
        val provider = object : ResourceContextProvider {
            override fun current(): ResourceContext? = ctx
        }

        assertThat(provider.current()).isSameAs(ctx)
        assertThat(provider.require()).isSameAs(ctx)
    }

    @Test
    fun `request path provider should return null when no path`() {
        val provider = object : RequestPathProvider {
            override fun current(): String? = null
        }

        assertThat(provider.current()).isNull()
    }

    @Test
    fun `request path provider should throw when require fails`() {
        val provider = object : RequestPathProvider {
            override fun current(): String? = null
        }

        assertThatThrownBy { provider.require() }
            .isInstanceOf(MissingRequestPathException::class.java)
            .hasMessage("Resolved request path is required.")
    }

    @Test
    fun `request path provider should return path when available`() {
        val provider = object : RequestPathProvider {
            override fun current(): String? = "/api/users"
        }

        assertThat(provider.current()).isEqualTo("/api/users")
        assertThat(provider.require()).isEqualTo("/api/users")
    }
}
