package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.tenancy.application.MissingActiveWorkspaceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HeaderActiveWorkspaceContextResolverTest {

    private val resolver = HeaderActiveWorkspaceContextResolver()

    @Test
    fun `resolves canonical raw UUID into workspace resource context`() {
        val workspaceId = "0199b1ca-0000-7000-8000-000000000001"
        val resourceContext = resolver.resolve(workspaceId)

        assertEquals(ResourceContextType.WORKSPACE, resourceContext.type)
        assertEquals(workspaceId, resourceContext.workspaceId)
    }

    @Test
    fun `accepts canonical raw workspace UUID`() {
        val context = resolver.resolve("0199b1ca-0000-7000-8000-000000000001")

        assertEquals("0199b1ca-0000-7000-8000-000000000001", context.workspaceId)
    }

    @Test
    fun `rejects prefixed workspace id`() {
        val error = assertThrows(InvalidActiveWorkspaceException::class.java) {
            resolver.resolve("ws-0199b1ca-0000-7000-8000-000000000001")
        }

        assertEquals("Active workspace identifier must be a UUID.", error.message)
    }

    @Test
    fun `rejects blank workspace id`() {
        val error = assertThrows(MissingActiveWorkspaceException::class.java) {
            resolver.resolve("   ")
        }

        assertEquals("Active workspace identifier is required.", error.message)
    }
}
