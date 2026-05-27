package com.profiletailors.common.testfixture

import com.profiletailors.common.domain.vo.credential.Credential
import kotlin.random.Random

/**
 * Shared helpers for generating credential-safe test passwords.
 *
 * Keep password creation centralized here so tests do not hardcode password literals.
 */
object CredentialGenerator {
    private const val MIN_PASSWORD_LENGTH = 12
    private const val REQUIRED_CHARACTER_GROUPS = 4

    private val uppercaseChars = ('A'..'Z').toList()
    private val lowercaseChars = ('a'..'z').toList()
    private val digitChars = ('0'..'9').toList()
    private val specialChars = listOf('@', '#', '$', '%', '&', '!')
    private val allChars = uppercaseChars + lowercaseChars + digitChars + specialChars

    fun generate(password: String = generateValidPassword()): Credential = Credential.create(password)

    fun generateValidPassword(length: Int = MIN_PASSWORD_LENGTH): String {
        require(length >= REQUIRED_CHARACTER_GROUPS) {
            "Password length must allow all required character groups"
        }

        val requiredChars = mutableListOf(
            uppercaseChars.random(),
            lowercaseChars.random(),
            digitChars.random(),
            specialChars.random(),
        )

        repeat(length - requiredChars.size) {
            requiredChars += allChars.random()
        }

        return requiredChars.shuffled(Random.Default).joinToString("")
    }
}
