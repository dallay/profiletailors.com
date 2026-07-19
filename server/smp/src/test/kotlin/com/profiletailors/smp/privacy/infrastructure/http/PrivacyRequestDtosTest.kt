package com.profiletailors.smp.privacy.infrastructure.http

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyRequestDtosTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `rejects empty type`() {
        val dto = SubmitPrivacyRequestDto(
            type = "",
            notes = null,
            newEmail = null,
            newUsername = null,
        )
        val violations = validator.validate(dto)
        assertTrue(violations.any { it.propertyPath.toString() == "type" })
    }

    @Test
    fun `rejects invalid email format in newEmail`() {
        val dto = SubmitPrivacyRequestDto(
            type = "CORRECTION",
            notes = null,
            newEmail = "not-an-email",
            newUsername = null,
        )
        val violations = validator.validate(dto)
        assertTrue(violations.any { it.propertyPath.toString() == "newEmail" })
    }

    @Test
    fun `rejects missing correction fields when type is CORRECTION`() {
        val dto = SubmitPrivacyRequestDto(
            type = "CORRECTION",
            notes = null,
            newEmail = null,
            newUsername = null,
        )
        val violations = validator.validate(dto)
        assertTrue(violations.any { it.message.contains("correction", ignoreCase = true) })
    }

    @Test
    fun `valid access request passes validation`() {
        val dto = SubmitPrivacyRequestDto(
            type = "ACCESS",
            notes = "Please provide my data",
            newEmail = null,
            newUsername = null,
        )
        val violations = validator.validate(dto)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `valid correction with newEmail passes validation`() {
        val dto = SubmitPrivacyRequestDto(
            type = "CORRECTION",
            notes = null,
            newEmail = "user@example.com",
            newUsername = null,
        )
        val violations = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    @Test
    fun `valid correction with newUsername passes validation`() {
        val dto = SubmitPrivacyRequestDto(
            type = "CORRECTION",
            notes = null,
            newEmail = null,
            newUsername = "newuser",
        )
        val violations = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Expected no violations but got: $violations")
    }

    @Test
    fun `valid email in newEmail passes`() {
        val dto = SubmitPrivacyRequestDto(
            type = "CORRECTION",
            notes = null,
            newEmail = "valid@example.com",
            newUsername = null,
        )
        val violations = validator.validate(dto)
        assertFalse(violations.any { it.propertyPath.toString() == "newEmail" })
    }
}
