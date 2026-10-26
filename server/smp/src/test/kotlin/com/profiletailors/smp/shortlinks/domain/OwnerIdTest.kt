package com.profiletailors.smp.shortlinks.domain

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OwnerIdTest {
    @Test
    fun `parses canonical raw UUID`() {
        val id = OwnerId.from("0199b1ca-0000-7000-8000-000000000001")

        assertEquals(UUID.fromString("0199b1ca-0000-7000-8000-000000000001"), id.value)
    }

    @Test
    fun `rejects prefixed identifier`() {
        shouldThrow<IllegalArgumentException> {
            OwnerId.from("ws-0199b1ca-0000-7000-8000-000000000001")
        }
    }
}
