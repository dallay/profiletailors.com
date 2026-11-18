package com.profiletailors.smp.platform.application

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.MissingRequestPathException
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.platform.infrastructure.ReactorContextPrincipalContextProvider
import com.profiletailors.smp.platform.infrastructure.ReactorContextRequestPathProvider
import com.profiletailors.smp.platform.infrastructure.ReactorContextResourceContextProvider
import com.profiletailors.smp.platform.infrastructure.RequestContextKeys
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import reactor.util.context.Context

class RequestContextProvidersTest {

    @Test
    fun `reactor context providers return principal resource and request path`() = runTest {
        val principal = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "user-123",
        )
        val resource = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "workspace-1",
        )
        val context = Context.of(RequestContextKeys.principal, principal)
            .put(RequestContextKeys.resource, resource)
            .put(RequestContextKeys.requestPath, "/api/media/assets")

        withContext(ReactorContext(context)) {
            assertSame(principal, ReactorContextPrincipalContextProvider().require())
            assertSame(resource, ReactorContextResourceContextProvider().require())
            assertEquals("/api/media/assets", ReactorContextRequestPathProvider().require())
        }
    }

    @Test
    fun `reactor context providers return null when context values are absent`() = runTest {
        assertNull(ReactorContextPrincipalContextProvider().current())
        assertNull(ReactorContextResourceContextProvider().current())
        assertNull(ReactorContextRequestPathProvider().current())
    }

    @Test
    fun `reactor context providers throw when required context values are absent`() = runTest {
        assertThrows(MissingPrincipalContextException::class.java) {
            kotlinx.coroutines.runBlocking { ReactorContextPrincipalContextProvider().require() }
        }
        assertThrows(MissingResourceContextException::class.java) {
            kotlinx.coroutines.runBlocking { ReactorContextResourceContextProvider().require() }
        }
        assertThrows(MissingRequestPathException::class.java) {
            kotlinx.coroutines.runBlocking { ReactorContextRequestPathProvider().require() }
        }
    }
}
