package com.profiletailors.smp.identity.application

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LocalPasswordCredentialGatewayUpdateTest {

    private val gateway = RecordingPasswordCredentialGateway()

    @Test
    fun `updatePasswordHash stores the new hash for the principal`() = runTest {
        gateway.create(principalId = "user-1", passwordHash = "old-hash")

        gateway.updatePasswordHash(principalId = "user-1", passwordHash = "new-hash")

        assertEquals(1, gateway.updateCalls)
        assertEquals("user-1", gateway.lastUpdatedPrincipalId)
        assertEquals("new-hash", gateway.lastUpdatedHash)
        assertEquals("new-hash", gateway.findByPrincipalId("user-1")?.passwordHash)
    }

    @Test
    fun `updatePasswordHash leaves other principals untouched`() = runTest {
        gateway.create(principalId = "user-1", passwordHash = "user-1-hash")
        gateway.create(principalId = "user-2", passwordHash = "user-2-hash")

        gateway.updatePasswordHash(principalId = "user-1", passwordHash = "new-user-1-hash")

        assertEquals("new-user-1-hash", gateway.findByPrincipalId("user-1")?.passwordHash)
        assertEquals("user-2-hash", gateway.findByPrincipalId("user-2")?.passwordHash)
        assertNotEquals(
            gateway.findByPrincipalId("user-1")?.passwordHash,
            gateway.findByPrincipalId("user-2")?.passwordHash,
        )
    }

    private class RecordingPasswordCredentialGateway : LocalPasswordCredentialGateway {
        private val records = mutableMapOf<String, LocalPasswordCredentialRecord>()
        var updateCalls: Int = 0
        var lastUpdatedPrincipalId: String? = null
        var lastUpdatedHash: String? = null

        override suspend fun create(principalId: String, passwordHash: String) {
            records[principalId] = LocalPasswordCredentialRecord(
                principalId = principalId,
                email = "$principalId@example.com",
                username = principalId,
                passwordHash = passwordHash,
            )
        }

        override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? =
            records.values.firstOrNull { it.email == email }

        override suspend fun updatePasswordHash(principalId: String, passwordHash: String) {
            updateCalls += 1
            lastUpdatedPrincipalId = principalId
            lastUpdatedHash = passwordHash
            val existing = records[principalId]
                ?: error("principalId $principalId not found in test fixture")
            records[principalId] = existing.copy(passwordHash = passwordHash)
        }

        fun findByPrincipalId(principalId: String): LocalPasswordCredentialRecord? = records[principalId]
    }
}
