package com.profiletailors.smp.mediaprovider.unsplash

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class UnsplashErrorMapperTest {

    private val mapper = UnsplashErrorMapper()

    @Test
    fun `maps ProviderErrorException to 502 with PROVIDER_ERROR code`() {
        val outcome = mapper.map(ProviderErrorException("upstream exploded"))
        assertEquals(HttpStatus.BAD_GATEWAY, outcome.status)
        assertEquals("PROVIDER_ERROR", outcome.errorCode)
    }

    @Test
    fun `maps ProviderUnavailableException to 504 with PROVIDER_UNREACHABLE code`() {
        val outcome = mapper.map(ProviderUnavailableException("timeout"))
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, outcome.status)
        assertEquals("PROVIDER_UNREACHABLE", outcome.errorCode)
    }

    @Test
    fun `maps ProviderImportRejectedException to 422 with IMPORT_REJECTED code`() {
        val outcome = mapper.map(ProviderImportRejectedException("binary too large"))
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, outcome.status)
        assertEquals("IMPORT_REJECTED", outcome.errorCode)
    }

    @Test
    fun `ProviderErrorException outcome carries retryAfter as null`() {
        val outcome = mapper.map(ProviderErrorException("429 with retry-after"))
        assertEquals(null, outcome.retryAfterSeconds)
    }

    @Test
    fun `maps UnsplashRateLimitedException to 429 with PROVIDER_RATE_LIMITED code`() {
        val outcome = mapper.map(UnsplashRateLimitedException(12))
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, outcome.status)
        assertEquals("PROVIDER_RATE_LIMITED", outcome.errorCode)
        assertEquals(12, outcome.retryAfterSeconds)
    }

    @Test
    fun `public message never leaks raw cause text or access key`() {
        val noisy = ProviderErrorException("Bearer abc123-secret-key-12345")
        val outcome = mapper.map(noisy)
        assertTrue(outcome.publicMessage.contains("Unsplash"))
        assertTrue(!outcome.publicMessage.contains("abc123-secret-key-12345"))
    }
}
