package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class ProviderCredentialGatewayTest {
    @Test
    fun `invalidating a credential prevents resolution`() = runTest {
        val gateway = RecordingProviderCredentialGateway()
        val credentialId = gateway.storeForOwner(
            "threads:user",
            UUID.randomUUID(),
            ProviderCredentials(
                provider = SocialProvider.THREADS,
                accessToken = "access-token",
                refreshToken = null,
                expiresAtEpochSeconds = null,
                scope = "threads_basic",
            ),
        )

        assertEquals("access-token", gateway.resolveCredential(credentialId).accessToken)
        gateway.invalidateCredential(credentialId)

        assertFalse(gateway.contains(credentialId))
    }

    private class RecordingProviderCredentialGateway : ProviderCredentialGateway {
        private val credentials = mutableMapOf<UUID, ProviderCredentials>()

        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: ProviderCredentials): UUID {
            val id = UUID.randomUUID()
            this.credentials[id] = credentials
            return id
        }

        override suspend fun resolveCredential(id: UUID): ProviderCredentials =
            credentials[id] ?: error("Credential not found")

        override suspend fun invalidateCredential(id: UUID) {
            credentials.remove(id)
        }

        fun contains(id: UUID): Boolean = credentials.containsKey(id)
    }
}
