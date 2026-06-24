package com.profiletailors.common.domain.presentation.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CursorEncoderTest {

    private val encoder: CursorEncoder = Base64CursorEncoder()

    @Test
    fun `should encode string to base64`() {
        val encoded = encoder.encode("hello")

        assertThat(encoded).isNotBlank()
        assertThat(encoded).doesNotContain("hello")
    }

    @Test
    fun `should decode encoded string`() {
        val original = "cursor-data"
        val encoded = encoder.encode(original)

        val decoded = encoder.decode(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `should roundtrip complex data`() {
        val original = "user:123:page:2:sort:createdAt"

        val result = encoder.decode(encoder.encode(original))

        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `should throw InvalidCursor on malformed data`() {
        org.junit.jupiter.api.assertThrows<InvalidCursor> {
            encoder.decode("!!!not-valid-base64!!!")
        }
    }

    @Test
    fun `should handle empty string`() {
        val decoded = encoder.decode("")

        assertThat(decoded).isEmpty()
    }
}
