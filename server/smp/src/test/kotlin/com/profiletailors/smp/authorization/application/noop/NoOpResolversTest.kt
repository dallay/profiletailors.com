package com.profiletailors.smp.authorization.application.noop

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NoOpResolversTest {

    private val principal = PrincipalContext("id", PrincipalType.SYSTEM, "system")
    private val resource = ResourceContext(ResourceContextType.GLOBAL)

    @Test
    fun `no-op direct grant resolver should return empty set`() = runTest {
        val resolver = NoOpDirectGrantResolver()

        val result = resolver.resolve(principal, resource)

        assertThat(result).isEmpty()
    }

    @Test
    fun `no-op scope resolver should return empty set`() = runTest {
        val resolver = NoOpScopeResolver()

        val result = resolver.resolve(principal, resource)

        assertThat(result).isEmpty()
    }

    @Test
    fun `no-op entitlement resolver should return empty set`() = runTest {
        val resolver = NoOpEntitlementResolver()

        val result = resolver.resolve(resource)

        assertThat(result).isEmpty()
    }
}
