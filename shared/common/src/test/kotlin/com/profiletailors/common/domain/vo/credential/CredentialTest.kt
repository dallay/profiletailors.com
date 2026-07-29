package com.profiletailors.common.domain.vo.credential

import com.profiletailors.common.testfixture.CredentialFixtures.BLANK_PASSWORD
import com.profiletailors.common.testfixture.CredentialFixtures.WEAK_PASSWORD_NO_LOWERCASE
import com.profiletailors.common.testfixture.CredentialFixtures.WEAK_PASSWORD_NO_NUMBER
import com.profiletailors.common.testfixture.CredentialFixtures.WEAK_PASSWORD_NO_SPECIAL
import com.profiletailors.common.testfixture.CredentialFixtures.WEAK_PASSWORD_NO_UPPERCASE
import com.profiletailors.common.testfixture.CredentialFixtures.WEAK_PASSWORD_TOO_SHORT
import com.profiletailors.common.testfixture.CredentialFixtures.WHITESPACE_PASSWORD
import com.profiletailors.common.testfixture.CredentialFixtures.aStrongCredential
import com.profiletailors.common.testfixture.CredentialFixtures.strongCredentialPassword
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class CredentialTest {

    @Test
    fun `should create a credential`() {
        val credential = aStrongCredential()
        assertThat(credential).isNotNull
        assertThat(credential.credentialValue.value).isNotBlank()
        assertEquals(credential.credentialValue.value, strongCredentialPassword)
    }

    @Test
    fun `should not create a credential with a weak password must have at least one number`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(WEAK_PASSWORD_NO_NUMBER)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one number")
    }

    @Test
    fun `should not create a credential with a weak password must have at least one uppercase`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(WEAK_PASSWORD_NO_UPPERCASE)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one uppercase character")
    }

    @Test
    fun `should not create a credential with a weak password must have at least one lowercase`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(WEAK_PASSWORD_NO_LOWERCASE)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one lowercase character")
    }

    @Test
    fun `should not create a credential with a weak password must have at least one special character`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(WEAK_PASSWORD_NO_SPECIAL)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one special character")
    }

    @Test
    fun `should not create a credential with a empty password`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(BLANK_PASSWORD)
        }
        assertThat(exception.message).isEqualTo("Credential value cannot be blank")
    }

    @Test
    fun `should not create a credential with a blank password`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(WHITESPACE_PASSWORD)
        }
        assertThat(exception.message).isEqualTo("Credential value cannot be blank")
    }

    @Test
    fun `should not create a credential with less than 8 characters`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(WEAK_PASSWORD_TOO_SHORT)
        }
        assertThat(exception.message).isEqualTo("Credential value must be at least 8 characters")
    }

    @Test
    fun `compare two credentials`() {
        val credential1 = aStrongCredential()
        val credential2 = aStrongCredential()
        assertThat(credential1).isNotEqualTo(credential2)
    }

    @Test
    fun `should generate a random password`() {
        val credentialPassword = Credential.generateRandomCredentialPassword()
        assertThat(credentialPassword).isNotBlank()
        assertThat(credentialPassword.length).isGreaterThanOrEqualTo(Credential.MIN_LENGTH)
        val credential = Credential.create(credentialPassword)
        assertThat(credential).isNotNull
        assertThat(credential.credentialValue.value).isNotBlank()
        assertEquals(credential.credentialValue.value, credentialPassword)
    }
}
