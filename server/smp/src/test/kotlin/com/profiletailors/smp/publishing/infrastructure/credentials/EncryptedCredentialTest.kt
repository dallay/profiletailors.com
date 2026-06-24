package com.profiletailors.smp.publishing.infrastructure.credentials

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class EncryptedCredentialTest {

    @Test
    fun `should create with all fields`() {
        val credential = EncryptedCredential(
            ownerType = "workspace",
            ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            encryptedPayload = byteArrayOf(1, 2, 3),
        )

        assertThat(credential.ownerType).isEqualTo("workspace")
        assertThat(credential.encryptedPayload).containsExactly(1, 2, 3)
        assertThat(credential.id).isNotNull
    }

    @Test
    fun `should auto-generate id`() {
        val c1 = EncryptedCredential(ownerType = "user", ownerId = UUID.randomUUID(), encryptedPayload = byteArrayOf())
        val c2 = EncryptedCredential(ownerType = "user", ownerId = UUID.randomUUID(), encryptedPayload = byteArrayOf())

        assertThat(c1.id).isNotEqualTo(c2.id)
    }
}
