package com.profiletailors.smp.authorization.application.resource.getpreview

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GetResourcePreviewQueryTest {

    @Test
    fun `should create query with resource id`() {
        val query = GetResourcePreviewQuery(resourceId = "res-123")

        assertThat(query.resourceId).isEqualTo("res-123")
    }

    @Test
    fun `should support data class equality`() {
        val q1 = GetResourcePreviewQuery("res-1")
        val q2 = GetResourcePreviewQuery("res-1")

        assertThat(q1).isEqualTo(q2)
    }
}
