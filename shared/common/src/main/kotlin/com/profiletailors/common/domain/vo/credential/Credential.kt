package com.profiletailors.common.domain.vo.credential

import com.profiletailors.common.domain.ValueObject
import com.profiletailors.common.domain.vo.credential.Credential.Companion.MIN_LENGTH
import com.profiletailors.common.domain.vo.credential.Credential.Companion.charLowercase
import com.profiletailors.common.domain.vo.credential.Credential.Companion.charNumbers
import com.profiletailors.common.domain.vo.credential.Credential.Companion.charSpecial
import com.profiletailors.common.domain.vo.credential.Credential.Companion.charUppercase
import com.profiletailors.common.domain.vo.credential.Credential.Companion.charset
import java.util.UUID

/**
 * A validated password credential value object.
 *
 * Bundles a unique [CredentialId] with a [CredentialValue] that enforces password policy:
 * minimum length (8), maximum length (128), and at least one character from each of four
 * categories: lowercase, uppercase, numeric, and special characters (`!@#$%^&*()_+{}|:<>?`).
 *
 * ## Factory methods
 * - [create]: Convenience factory that generates a random [CredentialId] and validates the
 *   raw password string. Use this for new credentials with an auto-generated ID.
 * - [fromRaw]: Factory that accepts an explicit [CredentialId]. Use this when reconstructing
 *   a credential from persistence where the ID must be preserved.
 *
 * ## Random generation
 * [generateRandomCredentialPassword] produces a cryptographically reasonable (but not
 * CSPRNG-guaranteed) password using `kotlin.random.Random`. For production use where
 * stronger entropy is needed, prefer a dedicated secure random source.
 *
 * @since 1.0.0
 * @see CredentialValue
 * @see CredentialException
 */
@ValueObject
data class Credential(val id: CredentialId, val credentialValue: CredentialValue) {
    companion object {
        private const val REQUIRED_TYPES = 4
        const val MIN_LENGTH = 8
        val charNumbers: CharRange = '0'..'9'
        val charUppercase: CharRange = 'A'..'Z'
        val charLowercase: CharRange = 'a'..'z'
        val charSpecial: List<Char> = "!@#$%^&*()_+{}|:<>?".toList()
        val charset: List<Char> = listOf(charLowercase, charUppercase, charNumbers, charSpecial).flatten()

        /**
         * Generates a cryptographically reasonable random password.
         *
         * Guarantees at least one character from each required category and a total
         * length between [MIN_LENGTH] and `2 * MIN_LENGTH`. The result is shuffled
         * so the mandated characters are not in a predictable position.
         *
         * @return a random password string satisfying all policy rules
         */
        fun generateRandomCredentialPassword(): String {
            val passwordChars = mutableListOf<Char>().apply {
                add(charNumbers.random())
                add(charUppercase.random())
                add(charLowercase.random())
                add(charSpecial.random())
            }
            val minLen = maxOf(MIN_LENGTH, REQUIRED_TYPES)
            val targetLength = minLen + kotlin.random.Random.nextInt(minLen)
            repeat(targetLength - passwordChars.size) { passwordChars.add(charset.random()) }
            return passwordChars.shuffled().joinToString("")
        }

        /**
         * Creates a new [Credential] with a randomly generated ID.
         *
         * @param credentialValue the raw password string to validate
         * @return a validated credential with a new [CredentialId]
         * @throws CredentialException if validation fails
         */
        fun create(credentialValue: String): Credential = fromRaw(CredentialId(UUID.randomUUID()), credentialValue)

        /**
         * Creates a [Credential] from an explicit ID and raw password.
         *
         * Use this when restoring a credential from persistence.
         *
         * @param id the credential's unique identifier
         * @param raw the raw password string to validate
         * @return a validated credential
         * @throws CredentialException if validation fails
         */
        fun fromRaw(id: CredentialId, raw: String): Credential = Credential(id, CredentialValue(raw))
    }
}

/**
 * Unique identifier for a [Credential].
 *
 * Wraps a [UUID] to provide type safety at the domain level.
 *
 * @since 1.0.0
 */
@ValueObject
@JvmInline
value class CredentialId(val value: UUID) {
    companion object {
        /** Creates a new random credential ID. */
        fun random(): CredentialId = CredentialId(UUID.randomUUID())
    }
}

/**
 * A validated raw credential (password) value.
 *
 * Enforces the full password policy in the `init` block:
 * - must not be blank
 * - length between [MIN_LENGTH] (8) and [MAX_CREDENTIAL_LENGTH] (128)
 * - must contain at least one number, one uppercase letter, one lowercase letter,
 *   and one special character
 *
 * [toString] is overridden to return `"****"` to prevent accidental credential exposure
 * in logs or serialization.
 *
 * @throws CredentialException if any validation rule is violated
 * @since 1.0.0
 */
@ValueObject
@JvmInline
value class CredentialValue(val value: String) {
    init {
        if (value.isBlank()) throw CredentialException("Credential value cannot be blank")
        if (value.length <
            MIN_LENGTH
        ) {
            throw CredentialException("Credential value must be at least $MIN_LENGTH characters")
        }
        if (value.length >
            MAX_CREDENTIAL_LENGTH
        ) {
            throw CredentialException("Credential value cannot exceed $MAX_CREDENTIAL_LENGTH characters")
        }
        if (!value.any { it in charNumbers }) throw CredentialException("The password must have at least one number")
        if (!value.any {
                it in charUppercase
            }
        ) {
            throw CredentialException("The password must have at least one uppercase character")
        }
        if (!value.any {
                it in charLowercase
            }
        ) {
            throw CredentialException("The password must have at least one lowercase character")
        }
        if (!value.any {
                it in charSpecial
            }
        ) {
            throw CredentialException("The password must have at least one special character")
        }
    }
    override fun toString(): String = "****"
    companion object {
        const val MAX_CREDENTIAL_LENGTH = 128
    }
}
