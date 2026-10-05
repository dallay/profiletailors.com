package com.profiletailors.smp.observability.infrastructure.sentry

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SentryEventPrivacySanitizerTest {

    @Test
    fun `before-send callback applies the privacy sanitizer`() {
        val event = SentryEvent().apply {
            setRequest(
                Request().apply {
                    setUrl("https://api.profiletailors.com/reset?code=reset-secret")
                    setCookies("pt_refresh=refresh-secret")
                },
            )
        }

        val sanitized = SentryPrivacyConfiguration()
            .sentryBeforeSend()
            .execute(event, Hint())

        assertThat(sanitized).isSameAs(event)
        assertThat(sanitized?.getRequest()?.getUrl()).isEqualTo("https://api.profiletailors.com/reset")
        assertThat(sanitized?.getRequest()?.getCookies()).isNull()
    }

    @Test
    fun `removes request metadata and user data while retaining a safe request location`() {
        val request = Request().apply {
            setUrl("https://app.profiletailors.com/reset-password?token=reset-secret")
            setQueryString("token=reset-secret")
            setFragment("access-secret")
            setData(mapOf("password" to "plaintext-secret"))
            setCookies("pt_refresh=refresh-secret")
            setHeaders(mapOf("Authorization" to "Bearer access-secret"))
            setEnvs(mapOf("REMOTE_ADDR" to "203.0.113.42"))
        }
        val event = SentryEvent().apply {
            setRequest(request)
            setUser(
                User().apply {
                    setId("principal-123")
                    setEmail("person@example.com")
                    setUsername("person")
                    setIpAddress("203.0.113.42")
                },
            )
            setTag("principalId", "principal-123")
            setTag("workspaceId", "workspace-456")
            setTag("email", "person@example.com")
            setBreadcrumbs(
                listOf(
                    Breadcrumb().apply {
                        setMessage("Request for person@example.com")
                        setData("url", "https://api.profiletailors.com/callback?code=oauth-secret")
                    },
                ),
            )
            setExtras(mapOf("payload" to "private-content"))
        }

        val sanitized = SentryEventPrivacySanitizer.sanitize(event)

        assertThat(sanitized.getRequest()?.getUrl())
            .isEqualTo("https://app.profiletailors.com/reset-password")
        assertThat(sanitized.getRequest()?.getQueryString()).isNull()
        assertThat(sanitized.getRequest()?.getFragment()).isNull()
        assertThat(sanitized.getRequest()?.getData()).isNull()
        assertThat(sanitized.getRequest()?.getCookies()).isNull()
        assertThat(sanitized.getRequest()?.getHeaders()).isNull()
        assertThat(sanitized.getRequest()?.getEnvs()).isNull()
        assertThat(sanitized.getUser()).isNull()
        assertThat(sanitized.getBreadcrumbs()).isNull()
        assertThat(sanitized.getExtras()).isNull()
        assertThat(sanitized.getTag("principalId")).isEqualTo("principal-123")
        assertThat(sanitized.getTag("workspaceId")).isEqualTo("workspace-456")
        assertThat(sanitized.getTag("email")).isNull()
    }

    @Test
    fun `sanitizes message and exception text before event delivery`() {
        val eventMessage = Message().apply {
            setFormatted("Failure for person@example.com at https://api.profiletailors.com/reset?token=reset-secret")
            setMessage("Authorization: Bearer access-secret")
            setParams(listOf("person@example.com"))
        }
        val exception = SentryException().apply {
            setType("IllegalStateException")
            setValue(
                "Failure for person@example.com from 2001:db8::1 at " +
                    "https://api.profiletailors.com/reset?token=reset-secret password=plaintext-secret " +
                    "api_key=api-secret oauth code=oauth-secret pt_refresh=refresh-secret",
            )
        }
        val event = SentryEvent().apply {
            setMessage(eventMessage)
            setExceptions(listOf(exception))
        }

        val sanitized = SentryEventPrivacySanitizer.sanitize(event)

        assertThat(sanitized.getMessage()?.getFormatted())
            .isEqualTo("Failure for [redacted] at https://api.profiletailors.com/reset")
        assertThat(sanitized.getMessage()?.getMessage()).isEqualTo("Authorization: [redacted]")
        assertThat(sanitized.getMessage()?.getParams()).isNull()
        val sanitizedExceptions = sanitized.getExceptions().orEmpty()
        assertThat(sanitizedExceptions).hasSize(1)
        assertThat(sanitizedExceptions.single().getValue())
            .isEqualTo(
                "Failure for [redacted] from [redacted] at https://api.profiletailors.com/reset " +
                    "password=[redacted] api_key=[redacted] oauth code=[redacted] pt_refresh=[redacted]",
            )
    }

    @Test
    fun `redacts literal IP hosts while retaining safe URL path context`() {
        val event = SentryEvent().apply {
            setRequest(
                Request().apply {
                    setUrl("https://203.0.113.42/reset?token=reset-secret")
                },
            )
        }

        val sanitized = SentryEventPrivacySanitizer.sanitize(event)

        assertThat(sanitized.getRequest()?.getUrl()).isEqualTo("https://redacted.invalid/reset")
    }

    @Test
    fun `bounds retained exception text`() {
        val event = SentryEvent().apply {
            setExceptions(
                listOf(
                    SentryException().apply {
                        setType("IllegalStateException")
                        setValue("x".repeat(600))
                    },
                ),
            )
        }

        val sanitized = SentryEventPrivacySanitizer.sanitize(event)

        assertThat(sanitized.getExceptions()?.single()?.getValue()).hasSize(512)
    }
}
