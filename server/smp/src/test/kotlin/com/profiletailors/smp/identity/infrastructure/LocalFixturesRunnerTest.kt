package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.LocalPasswordCredentialGateway
import com.profiletailors.smp.identity.application.LocalPasswordCredentialRecord
import com.profiletailors.smp.identity.application.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFixturesRunnerTest {

    private val captures = mutableListOf<Pair<String, String>>()

    private val fakeGateway = object : LocalPasswordCredentialGateway {
        var existing: LocalPasswordCredentialRecord? = null

        override suspend fun create(principalId: String, passwordHash: String) {
            captures.add(principalId to passwordHash)
        }

        override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? = existing
    }

    private val fakeHasher = object : PasswordHasher {
        override val algorithm: String = "fake"

        var lastInput: String? = null

        override fun hash(rawPassword: String): String {
            lastInput = rawPassword
            return "hashed:$rawPassword"
        }

        override fun matches(rawPassword: String, passwordHash: String): Boolean = passwordHash == "hashed:$rawPassword"
    }

    @Test
    fun `creates credential for dev user when none exists`() {
        val runner = LocalFixturesRunner(fakeGateway, fakeHasher)
        assertDoesNotThrow { runBlocking { runner.seedDevCredential() } }

        assertEquals(1, captures.size, "Should create exactly one credential")
        assertEquals("dev-user-001", captures[0].first)
        assertEquals("hashed:S3cr3tP@ssw0rd*123", captures[0].second)
        assertEquals("S3cr3tP@ssw0rd*123", fakeHasher.lastInput)
    }

    @Test
    fun `skips creation when credential already exists`() {
        fakeGateway.existing = LocalPasswordCredentialRecord(
            principalId = "dev-user-001",
            email = "dev@profiletailors.com",
            username = "dev",
            passwordHash = "existing-hash",
        )

        val runner = LocalFixturesRunner(fakeGateway, fakeHasher)
        assertDoesNotThrow { runBlocking { runner.seedDevCredential() } }

        assertTrue(captures.isEmpty(), "Should not create credential when one already exists")
        assertNull(fakeHasher.lastInput, "Should not hash when credential is already present")
    }
}
