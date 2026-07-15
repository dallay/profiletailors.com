package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.UnsplashPhotoNotFoundException
import com.profiletailors.smp.media.application.UnsplashPhotoTooLargeException
import com.profiletailors.smp.media.application.UnsplashProviderException
import com.profiletailors.smp.media.application.UnsplashProviderNotConfiguredException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class UnsplashProblemDetailsHandlerTest {

    private val handler = UnsplashProblemDetailsHandler()

    @Test
    fun `not configured exception maps to 503 problem detail`() {
        val result = handler.handle(UnsplashProviderNotConfiguredException())

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.status)
        assertEquals("Unsplash is not configured", result.title)
        assertEquals("Unsplash is not configured for this environment.", result.detail)
        assertEquals("UNSPLASH_NOT_CONFIGURED", result.properties?.get("errorCode"))
    }

    @Test
    fun `photo not found exception maps to 404 with external id`() {
        val result = handler.handle(UnsplashPhotoNotFoundException("photo-404"))

        assertEquals(HttpStatus.NOT_FOUND.value(), result.status)
        assertEquals("Unsplash photo not found", result.title)
        assertEquals("Unsplash photo photo-404 was not found.", result.detail)
        assertEquals("UNSPLASH_PHOTO_NOT_FOUND", result.properties?.get("errorCode"))
        assertEquals("photo-404", result.properties?.get("externalId"))
    }

    @Test
    fun `photo too large exception maps to 413 with size properties`() {
        val result = handler.handle(UnsplashPhotoTooLargeException(actualSize = 12_000_000, maxAllowed = 10_000_000))

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), result.status)
        assertEquals("Unsplash photo too large", result.title)
        assertEquals("UNSPLASH_PHOTO_TOO_LARGE", result.properties?.get("errorCode"))
        assertEquals(12_000_000L, result.properties?.get("actualSize"))
        assertEquals(10_000_000L, result.properties?.get("maxAllowed"))
    }

    @Test
    fun `provider exception maps to 502 problem detail`() {
        val result = handler.handle(UnsplashProviderException("Unsplash timed out"))

        assertEquals(HttpStatus.BAD_GATEWAY.value(), result.status)
        assertEquals("Unsplash provider error", result.title)
        assertEquals("Unsplash timed out", result.detail)
        assertEquals("UNSPLASH_PROVIDER_ERROR", result.properties?.get("errorCode"))
    }
}
