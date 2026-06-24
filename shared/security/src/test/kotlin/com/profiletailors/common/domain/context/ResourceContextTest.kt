package com.profiletailors.common.domain.context

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ResourceContextTest {

    @Test
    fun `should create with type only`() {
        val ctx = ResourceContext(type = ResourceContextType.GLOBAL)

        assertThat(ctx.type).isEqualTo(ResourceContextType.GLOBAL)
    }

    @Test
    fun `should default optional fields`() {
        val ctx = ResourceContext(type = ResourceContextType.GLOBAL)

        assertThat(ctx.workspaceId).isNull()
        assertThat(ctx.resourceOwnerId).isNull()
        assertThat(ctx.targetResourceType).isNull()
        assertThat(ctx.targetResourceId).isNull()
        assertThat(ctx.scopeHints).isEmpty()
    }

    @Test
    fun `should create with all fields`() {
        val ctx = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "ws-1",
            resourceOwnerId = "owner-1",
            targetResourceType = "post",
            targetResourceId = "post-123",
            scopeHints = setOf("read", "write"),
        )

        assertThat(ctx.type).isEqualTo(ResourceContextType.WORKSPACE)
        assertThat(ctx.workspaceId).isEqualTo("ws-1")
        assertThat(ctx.resourceOwnerId).isEqualTo("owner-1")
        assertThat(ctx.targetResourceType).isEqualTo("post")
        assertThat(ctx.targetResourceId).isEqualTo("post-123")
        assertThat(ctx.scopeHints).containsExactly("read", "write")
    }

    @Test
    fun `should support data class equality`() {
        val ctx1 = ResourceContext(ResourceContextType.GLOBAL)
        val ctx2 = ResourceContext(ResourceContextType.GLOBAL)

        assertThat(ctx1).isEqualTo(ctx2)
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode())
    }

    @Test
    fun `should distinguish different resource types`() {
        val global = ResourceContext(ResourceContextType.GLOBAL)
        val workspace = ResourceContext(ResourceContextType.WORKSPACE)

        assertThat(global).isNotEqualTo(workspace)
    }
}
