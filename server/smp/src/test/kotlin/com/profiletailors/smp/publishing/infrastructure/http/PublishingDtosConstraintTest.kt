package com.profiletailors.smp.publishing.infrastructure.http

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates that publishing request DTOs enforce basic shape constraints.
 *
 * These tests exercise [Validator.validate] directly against the DTO classes,
 * without Spring MVC or a full application context.  They confirm that fields
 * annotated with `@field:NotBlank` reject blank strings and that valid
 * inputs pass without errors.
 *
 * No business moderation (profanity, spam, content policy) is tested here —
 * that belongs at the handler or service layer.
 */
class PublishingDtosConstraintTest {

    private val validator: Validator by lazy {
        val factory: ValidatorFactory = Validation.buildDefaultValidatorFactory()
        factory.validator
    }

    // ── LinkedInConnectionInitiationRequest ───────────────────────────────────

    @Test
    fun `redirectUri blank is rejected`() {
        val violations = validator.validate(
            LinkedInConnectionInitiationRequest(redirectUri = ""),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "redirectUri" })
    }

    @Test
    fun `redirectUri whitespace-only is rejected`() {
        val violations = validator.validate(
            LinkedInConnectionInitiationRequest(redirectUri = "   "),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "redirectUri" })
    }

    @Test
    fun `redirectUri valid passes`() {
        val violations = validator.validate(
            LinkedInConnectionInitiationRequest(redirectUri = "https://app.example.com/callback"),
        )
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    // ── LinkedInConnectionCompletionRequest ───────────────────────────────────

    @Test
    fun `authorizationCode blank is rejected`() {
        val violations = validator.validate(
            LinkedInConnectionCompletionRequest(
                authorizationCode = "",
                redirectUri = "https://app.example.com/callback",
                state = "signed-state",
            ),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "authorizationCode" })
    }

    @Test
    fun `completion redirectUri blank is rejected`() {
        val violations = validator.validate(
            LinkedInConnectionCompletionRequest(
                authorizationCode = "auth-code",
                redirectUri = "",
                state = "signed-state",
            ),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "redirectUri" })
    }

    @Test
    fun `state blank is rejected`() {
        val violations = validator.validate(
            LinkedInConnectionCompletionRequest(
                authorizationCode = "auth-code",
                redirectUri = "https://app.example.com/callback",
                state = "",
            ),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "state" })
    }

    @Test
    fun `completion all fields valid passes`() {
        val violations = validator.validate(
            LinkedInConnectionCompletionRequest(
                authorizationCode = "auth-code",
                redirectUri = "https://app.example.com/callback",
                state = "signed-state",
            ),
        )
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    // ── PublicationUpsertRequest ─────────────────────────────────────────────

    @Test
    fun `socialAccountId blank is rejected`() {
        val violations = validator.validate(
            PublicationUpsertRequest(
                socialAccountId = "",
                scheduleMode = "NOW",
            ),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "socialAccountId" })
    }

    @Test
    fun `socialAccountId whitespace-only is rejected`() {
        val violations = validator.validate(
            PublicationUpsertRequest(
                socialAccountId = "   ",
                scheduleMode = "NOW",
            ),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "socialAccountId" })
    }

    @Test
    fun `optional title null is allowed`() {
        val violations = validator.validate(
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                title = null,
                scheduleMode = "NOW",
            ),
        )
        assertFalse(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `optional bodyText null is allowed`() {
        val violations = validator.validate(
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                bodyText = null,
                scheduleMode = "NOW",
            ),
        )
        assertFalse(violations.any { it.propertyPath.toString() == "bodyText" })
    }

    @Test
    fun `empty assetIds list is allowed`() {
        val violations = validator.validate(
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                assetIds = emptyList(),
                scheduleMode = "NOW",
            ),
        )
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    @Test
    fun `upsert with all fields valid passes`() {
        val scheduledFor = Instant.parse("2026-06-20T14:00:00Z")
        val violations = validator.validate(
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                title = "Hello World",
                bodyText = "This is a test post",
                assetIds = listOf("asset-1", "asset-2"),
                scheduleMode = "SCHEDULED_AT",
                scheduledFor = scheduledFor,
                priority = true,
            ),
        )
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    // ── QuickCreateRequest ────────────────────────────────────────────────────

    @Test
    fun `quickCreate socialAccountId blank is rejected`() {
        val violations = validator.validate(
            QuickCreateRequest(
                socialAccountId = "",
                scheduledFor = Instant.parse("2026-06-20T14:00:00Z"),
            ),
        )
        assertTrue(violations.any { it.propertyPath.toString() == "socialAccountId" })
    }

    @Test
    fun `quickCreate all fields valid passes`() {
        val violations = validator.validate(
            QuickCreateRequest(
                socialAccountId = "account-1",
                title = "Quick Post",
                bodyText = "Quick content",
                scheduledFor = Instant.parse("2026-06-20T14:00:00Z"),
                priority = false,
            ),
        )
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    // ── Constraint violation messages are meaningful ───────────────────────────

    @Test
    fun `violations carry a non-blank message`() {
        val violations = validator.validate(
            LinkedInConnectionInitiationRequest(redirectUri = ""),
        )
        val violation = violations.first()
        assertNotNull(violation.message)
        assertNotEquals("", violation.message)
        assertTrue(violation.message.isNotBlank())
    }
}
