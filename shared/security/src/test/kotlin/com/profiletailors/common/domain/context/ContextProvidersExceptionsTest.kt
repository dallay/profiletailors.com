package com.profiletailors.common.domain.context

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ContextProvidersExceptionsTest {

    @Test
    fun `PrincipalContextProvider require should throw if current is null`() = runBlocking {
        val provider = object : PrincipalContextProvider {
            override suspend fun current(): PrincipalContext? = null
        }

        assertThrows<MissingPrincipalContextException> {
            runBlocking { provider.require() }
        }
    }

    @Test
    fun `ResourceContextProvider require should throw if current is null`() = runBlocking {
        val provider = object : ResourceContextProvider {
            override suspend fun current(): ResourceContext? = null
        }

        assertThrows<MissingResourceContextException> {
            runBlocking { provider.require() }
        }
    }

    @Test
    fun `RequestPathProvider require should throw if current is null`() = runBlocking {
        val provider = object : RequestPathProvider {
            override suspend fun current(): String? = null
        }

        assertThrows<MissingRequestPathException> {
            runBlocking { provider.require() }
        }
    }
}
