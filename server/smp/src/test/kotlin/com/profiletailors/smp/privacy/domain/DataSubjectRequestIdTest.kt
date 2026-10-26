package com.profiletailors.smp.privacy.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class DataSubjectRequestIdTest {

    @Test
    fun `data subject request id preserves canonical UUID`() {
        val value = "123e4567-e89b-12d3-a456-426614174000"

        assertEquals(value, DataSubjectRequestId(value).value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            " ",
            "dsr-123e4567-e89b-12d3-a456-426614174000",
            "dsr-invalid",
            "not-a-uuid",
            "1-1-1-1-1",
            "123e4567e89b12d3a456426614174000",
            "123E4567-E89B-12D3-A456-426614174000",
            " 123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174000 ",
        ],
    )
    fun `data subject request id rejects noncanonical UUID`(value: String) {
        assertThrows<IllegalArgumentException> { DataSubjectRequestId(value) }
    }

    @Test
    fun `random creates distinct canonical UUID identifiers`() {
        val first = DataSubjectRequestId.random()
        val second = DataSubjectRequestId.random()

        assertEquals(UUID.fromString(first.value).toString(), first.value)
        assertEquals(UUID.fromString(second.value).toString(), second.value)
        assertNotEquals(first, second)
    }
}
