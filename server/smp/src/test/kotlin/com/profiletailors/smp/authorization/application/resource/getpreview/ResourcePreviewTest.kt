package com.profiletailors.smp.authorization.application.resource.getpreview

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ResourcePreviewTest {

    @Test
    fun `should create resource preview`() {
        val preview = ResourcePreview(
            workspaceId = "ws-1",
            resourceId = "res-1",
            principalId = "user-1",
            previewAllowed = true,
        )

        assertThat(preview.workspaceId).isEqualTo("ws-1")
        assertThat(preview.resourceId).isEqualTo("res-1")
        assertThat(preview.principalId).isEqualTo("user-1")
        assertThat(preview.previewAllowed).isTrue()
    }
}
