package com.profiletailors.common.testfixture

import com.profiletailors.common.domain.vo.credential.Credential

/**
 * Test fixtures for [Credential] value object.
 * All passwords are intentionally safe/weak examples used ONLY in tests.
 * Use [strongCredentialPassword] for happy-path tests.
 * Use [WEAK_PASSWORD_NO_NUMBER], [WEAK_PASSWORD_NO_UPPERCASE], etc. for validation error tests.
 */
object CredentialFixtures {

    /** Safe password passing all validation rules — use in happy-path tests. */
    val strongCredentialPassword: String = CredentialGenerator.generateValidPassword()

    /** Creates a [Credential] from the strong test password. */
    fun aStrongCredential(): Credential = CredentialGenerator.generate(strongCredentialPassword)

    // --- Weak passwords for validation error tests ---

    /** Missing: number */
    const val WEAK_PASSWORD_NO_NUMBER = "Weakpassword"

    /** Missing: uppercase */
    const val WEAK_PASSWORD_NO_UPPERCASE = "weakpassword1"

    /** Missing: lowercase */
    const val WEAK_PASSWORD_NO_LOWERCASE = "WEAKPASSWORD1"

    /** Missing: special character */
    const val WEAK_PASSWORD_NO_SPECIAL = "Weakpassword1"

    /** Too short: less than 8 characters */
    const val WEAK_PASSWORD_TOO_SHORT = "Weak@1"

    /** Blank: empty string */
    const val BLANK_PASSWORD = ""

    /** Blank: whitespace only */
    const val WHITESPACE_PASSWORD = "   "

    /**
     * Builds a weak password missing exactly one validation rule.
     * Useful for parameterized tests covering all failure modes.
     */
    fun weakByRemovingRule(
        removeNumber: Boolean = false,
        removeUppercase: Boolean = false,
        removeLowercase: Boolean = false,
        removeSpecial: Boolean = false,
    ): String {
        var password = strongCredentialPassword
        if (removeNumber) password = password.filterNot { it.isDigit() }
        if (removeUppercase) password = password.filterNot { it.isUpperCase() }
        if (removeLowercase) password = password.filterNot { it.isLowerCase() }
        if (removeSpecial) password = password.filterNot { !it.isLetterOrDigit() }
        return password
    }
}
