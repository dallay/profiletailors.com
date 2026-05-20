package com.profiletailors.common.domain.vo.credential

import com.profiletailors.common.testfixture.CredentialFixtures.strongCredentialPassword
import com.profiletailors.common.testfixture.CredentialFixtures.weakPasswordNoNumber
import com.profiletailors.common.testfixture.CredentialFixtures.weakPasswordNoSpecial
import com.profiletailors.common.testfixture.CredentialFixtures.weakPasswordNoUppercase
import com.profiletailors.common.testfixture.CredentialFixtures.weakPasswordTooShort
import com.profiletailors.common.testfixture.CredentialFixtures.blankPassword
import com.profiletailors.common.testfixture.CredentialFixtures.whitespacePassword
import com.profiletailors.common.testfixture.CredentialFixtures.weakPasswordNoLowercase
import com.profiletailors.common.testfixture.CredentialFixtures.aStrongCredential
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
            Credential.create(weakPasswordNoNumber)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one number")
    }

    @Test
    fun `should not create a credential with a weak password must have at least one uppercase`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(weakPasswordNoUppercase)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one uppercase character")
    }

    @Test
    fun `should not create a credential with a weak password must have at least one lowercase`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(weakPasswordNoLowercase)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one lowercase character")
    }

    @Test
    fun `should not create a credential with a weak password must have at least one special character`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(weakPasswordNoSpecial)
        }
        assertThat(exception.message).isEqualTo("The password must have at least one special character")
    }

    @Test
    fun `should not create a credential with a empty password`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(blankPassword)
        }
        assertThat(exception.message).isEqualTo("Credential value cannot be blank")
    }

    @Test
    fun `should not create a credential with a blank password`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(whitespacePassword)
        }
        assertThat(exception.message).isEqualTo("Credential value cannot be blank")
    }

    @Test
    fun `should not create a credential with less than 8 characters`() {
        val exception = assertThrows(CredentialException::class.java) {
            Credential.create(weakPasswordTooShort)
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