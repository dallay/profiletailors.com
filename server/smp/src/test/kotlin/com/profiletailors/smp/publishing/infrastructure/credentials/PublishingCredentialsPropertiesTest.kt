package com.profiletailors.smp.publishing.infrastructure.credentials

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PublishingCredentialsPropertiesTest {

    @Test
    fun `should default key to null`() {
        val properties = PublishingCredentialsProperties()

        properties.key shouldBe null
    }

    @Test
    fun `should store assigned key value`() {
        val properties = PublishingCredentialsProperties()

        properties.key = "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3ODkwMTI="

        properties.key shouldBe "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3ODkwMTI="
    }

    @Test
    fun `should allow resetting key back to null`() {
        val properties = PublishingCredentialsProperties().apply {
            key = "some-key"
        }

        properties.key = null

        properties.key shouldBe null
    }
}