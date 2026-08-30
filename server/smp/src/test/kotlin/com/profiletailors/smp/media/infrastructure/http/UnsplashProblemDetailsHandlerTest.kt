package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.UnsplashPhotoNotFoundException
import com.profiletailors.smp.media.application.UnsplashPhotoTooLargeException
import com.profiletailors.smp.media.application.UnsplashProviderException
import com.profiletailors.smp.media.application.UnsplashProviderNotConfiguredException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class UnsplashProblemDetailsHandlerTest {

    private val handler = UnsplashProblemDetailsHandler()

    @Test
    fun `not configured exception maps to 503 problem detail`() {
        val result = handler.handle(UnsplashProviderNotConfiguredException())

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        result.title shouldBe "Unsplash is not configured"
        result.detail shouldBe "Unsplash is not configured for this environment."
        result.properties?.get("errorCode") shouldBe "UNSPLASH_NOT_CONFIGURED"
    }

    @Test
    fun `photo not found exception maps to 404 with id omitted`() {
        val result = handler.handle(UnsplashPhotoNotFoundException("photo-404"))

        result.status shouldBe HttpStatus.NOT_FOUND.value()
        result.title shouldBe "Unsplash photo not found"
        result.detail shouldBe "Unsplash photo not found."
        result.properties?.get("errorCode") shouldBe "UNSPLASH_PHOTO_NOT_FOUND"
        result.properties?.get("externalId") shouldBe null
    }

    @Test
    fun `photo too large exception maps to 413 with size properties`() {
        val result = handler.handle(UnsplashPhotoTooLargeException(actualSize = 12_000_000, maxAllowed = 10_000_000))

        result.status shouldBe HttpStatus.CONTENT_TOO_LARGE.value()
        result.title shouldBe "Unsplash photo too large"
        result.properties?.get("errorCode") shouldBe "UNSPLASH_PHOTO_TOO_LARGE"
        result.properties?.get("actualSize") shouldBe 12_000_000L
        result.properties?.get("maxAllowed") shouldBe 10_000_000L
    }

    @Test
    fun `provider exception maps to 502 problem detail`() {
        val result = handler.handle(UnsplashProviderException("Unsplash timed out"))

        result.status shouldBe HttpStatus.BAD_GATEWAY.value()
        result.title shouldBe "Unsplash provider error"
        result.detail shouldBe "Unsplash is temporarily unavailable."
        result.properties?.get("errorCode") shouldBe "UNSPLASH_PROVIDER_ERROR"
    }
}
