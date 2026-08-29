package com.profiletailors.smp.mcp.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Tag("fast")
class IdempotencyKeyHasherTest {

    @Test
    fun `produces a 64-character hex SHA-256 hash`() {
        val hash = IdempotencyKeyHasher.hash("agent-retry-1")
        assertThat(hash).hasSize(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    @Test
    fun `is deterministic`() {
        val first = IdempotencyKeyHasher.hash("k1")
        val second = IdempotencyKeyHasher.hash("k1")
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `different plaintexts produce different hashes`() {
        val a = IdempotencyKeyHasher.hash("k1")
        val b = IdempotencyKeyHasher.hash("k2")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `rejects blank plaintext`() {
        assertThrows<IllegalArgumentException> {
            IdempotencyKeyHasher.hash("")
        }
        assertThrows<IllegalArgumentException> {
            IdempotencyKeyHasher.hash("   ")
        }
    }

    @Test
    fun `isValidHash accepts sha-256 hex`() {
        val valid = IdempotencyKeyHasher.hash("k1")
        assertThat(IdempotencyKeyHasher.isValidHash(valid)).isTrue()
    }

    @Test
    fun `isValidHash rejects non-hex or wrong length`() {
        assertThat(IdempotencyKeyHasher.isValidHash("not-a-hash")).isFalse()
        assertThat(IdempotencyKeyHasher.isValidHash("A".repeat(64))).isFalse()
        assertThat(IdempotencyKeyHasher.isValidHash("")).isFalse()
        assertThat(IdempotencyKeyHasher.isValidHash("a".repeat(63))).isFalse()
    }

    @Test
    fun `hash does not contain the plaintext`() {
        val plaintext = "super-secret-key-do-not-leak"
        val hash = IdempotencyKeyHasher.hash(plaintext)
        assertThat(hash).doesNotContain(plaintext)
    }
}
