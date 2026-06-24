package com.profiletailors.common.domain.presentation.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CursorTest {

    @Test
    fun `default cursor should be TimestampCursor`() {
        val default = Cursor.default()

        assertThat(default).isInstanceOf(TimestampCursor::class.java)
    }

    @Test
    fun `encode and decode should roundtrip`() {
        val original = "cursor-data-123"

        val encoded = Cursor.encode(original)
        val decoded = Cursor.decode(encoded)

        assertThat(decoded).isEqualTo(original)
        assertThat(encoded).isNotEqualTo(original)
    }

    @Test
    fun `encode should produce non-empty string`() {
        val encoded = Cursor.encode("test")

        assertThat(encoded).isNotBlank()
    }

    @Test
    fun `decode should throw on malformed input`() {
        org.junit.jupiter.api.assertThrows<InvalidCursor> {
            Cursor.decode("!!!invalid-base64!!!")
        }
    }
}
