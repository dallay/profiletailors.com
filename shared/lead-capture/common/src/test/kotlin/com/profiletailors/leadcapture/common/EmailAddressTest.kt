package com.profiletailors.leadcapture.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class EmailAddressTest {

    @Test
    fun `valid email is preserved as-is`() {
        val email = EmailAddress("User.Example@domain.com")
        assertEquals("User.Example@domain.com", email.value)
        assertEquals("User.Example@domain.com", email.toString())
    }

    @Test
    fun `valid email with subdomain is accepted`() {
        val email = EmailAddress("user@sub.domain.org")
        assertEquals("user@sub.domain.org", email.value)
    }

    @Test
    fun `blank email is rejected`() {
        assertThrows<IllegalArgumentException> { EmailAddress("") }
        assertThrows<IllegalArgumentException> { EmailAddress("   ") }
    }

    @Test
    fun `email without at sign is rejected`() {
        assertThrows<IllegalArgumentException> { EmailAddress("notanemail") }
    }

    @Test
    fun `email without local part is rejected`() {
        assertThrows<IllegalArgumentException> { EmailAddress("@domain.com") }
    }

    @Test
    fun `email without domain is rejected`() {
        assertThrows<IllegalArgumentException> { EmailAddress("user@") }
    }

    @Test
    fun `email with spaces is rejected`() {
        assertThrows<IllegalArgumentException> { EmailAddress("user @domain.com") }
    }

    @Test
    fun `plus addressing is preserved`() {
        val email = EmailAddress("user+tag@example.com")
        assertEquals("user+tag@example.com", email.value)
    }

    @Test
    fun `dots in local part are preserved`() {
        val email = EmailAddress("u.s.e.r@gmail.com")
        assertEquals("u.s.e.r@gmail.com", email.value)
    }

    @Test
    fun `equality is value-based`() {
        val email1 = EmailAddress("user@example.com")
        val email2 = EmailAddress("user@example.com")
        assertEquals(email1, email2)
        assertEquals(email1.hashCode(), email2.hashCode())
    }
}
