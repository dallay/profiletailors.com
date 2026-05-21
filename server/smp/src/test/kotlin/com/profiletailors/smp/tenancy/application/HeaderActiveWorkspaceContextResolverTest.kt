package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContextType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HeaderActiveWorkspaceContextResolverTest {

    private val resolver = HeaderActiveWorkspaceContextResolver()

    @Test
    fun `resolves explicit workspace id into workspace resource context`() {
        val resourceContext = resolver.resolve("workspace-123")

        assertEquals(ResourceContextType.WORKSPACE, resourceContext.type)
        assertEquals("workspace-123", resourceContext.workspaceId)
    }

    @Test
    fun `rejects blank workspace id`() {
        val error = assertThrows(MissingActiveWorkspaceException::class.java) {
            resolver.resolve("   ")
        }

        assertEquals("Active workspace identifier is required.", error.message)
    }
}
