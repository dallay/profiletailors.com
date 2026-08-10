package com.profiletailors.smp.privacy.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class DataSubjectRequestIdTest {

    @Test
    fun `data subject request id rejects values without dsr prefix`() {
        assertThrows<IllegalArgumentException> {
            DataSubjectRequestId("123e4567-e89b-12d3-a456-426614174000")
        }
    }

    @Test
    fun `data subject request id accepts dsr prefixed values`() {
        val value = "dsr-123e4567-e89b-12d3-a456-426614174000"

        val id = DataSubjectRequestId(value)

        assertEquals(value, id.value)
    }

    @Test
    fun `random creates id with dsr prefix`() {
        val id = DataSubjectRequestId.random()

        assertEquals(true, id.value.startsWith("dsr-"))
    }
}
