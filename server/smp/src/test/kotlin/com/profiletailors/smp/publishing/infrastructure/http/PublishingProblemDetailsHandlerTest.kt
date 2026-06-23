package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.SocialProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PublishingProblemDetailsHandlerTest {
    private val handler = PublishingProblemDetailsHandler()

    @Test
    fun `maps ProviderNotConfiguredException to 503 SERVICE_UNAVAILABLE`() {
        val exception = ProviderNotConfiguredException(SocialProvider.LINKEDIN)
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), problem.status)
        assertEquals("Provider not configured", problem.title)
        assertEquals("Provider 'LINKEDIN' is not configured.", problem.detail)
    }

    @Test
    fun `maps ExpiredOAuthStateException to 400 BAD_REQUEST`() {
        val exception = ExpiredOAuthStateException()
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("OAuth state expired", problem.title)
        assertEquals("OAuth state has expired.", problem.detail)
    }

    @Test
    fun `maps InvalidOAuthStateException to 400 BAD_REQUEST`() {
        val exception = InvalidOAuthStateException("Custom message")
        val problem = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("OAuth state invalid", problem.title)
        assertEquals("Custom message", problem.detail)
    }
}
