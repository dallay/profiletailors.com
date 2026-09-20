package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.infrastructure.http.RegistrationModeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JacksonConfigurationIdempotencyCodecTest {

    private val codec = JacksonConfigurationIdempotencyCodec()

    @Test
    fun `should round-trip registration mode results through json`() {
        val result = RegistrationModeResult("INVITE_ONLY")

        val decoded = codec.decode(codec.encode(result), RegistrationModeResult::class.java)

        assertEquals(result, decoded)
    }
}
