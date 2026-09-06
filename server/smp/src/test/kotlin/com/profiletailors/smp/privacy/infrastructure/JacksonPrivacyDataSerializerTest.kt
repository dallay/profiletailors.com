package com.profiletailors.smp.privacy.infrastructure

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JacksonPrivacyDataSerializerTest {
    private val serializer = JacksonPrivacyDataSerializer()

    @Test
    fun `serializes nested privacy data as JSON`() {
        val data = linkedMapOf(
            "identity" to mapOf("email" to "user@example.com"),
            "workspaces" to listOf("workspace-1"),
        )

        serializer.toJson(data) shouldBe
            "{\"identity\":{\"email\":\"user@example.com\"},\"workspaces\":[\"workspace-1\"]}"
    }

    @Test
    fun `serializes null as JSON null`() {
        serializer.toJson(null) shouldBe "null"
    }
}
