package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.model.UserControlResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JacksonUserControlIdempotencyCodecTest {

    private val codec = JacksonUserControlIdempotencyCodec()

    @Test
    fun `round-trips control results through json`() {
        val result = UserControlResult("user-1", UserAccountState.DISABLED, 2)

        val decoded = codec.decode(codec.encode(result), UserControlResult::class.java)

        assertEquals(result, decoded)
    }
}
