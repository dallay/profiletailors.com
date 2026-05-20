package com.profiletailors.common.domain.security

import com.profiletailors.common.infrastructure.security.HmacHasher
import com.profiletailors.common.infrastructure.security.Sha256Hasher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class HasherTest {

    @Test
    fun `sha256 returns expected_hash_for_known_inputs`() {
        val hasher = Sha256Hasher()

        val hello = "hello"
        val expectedHello = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expectedHello, hasher.hash(hello))

        val empty = ""
        val expectedEmpty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expectedEmpty, hasher.hash(empty))
    }

    @Test
    fun `hmacsha256 produces_consistent_hmac_given_secret_and_input`() {
        val secret = "bede9afc9f2bfdccdbddfd742eeff0d6b8ad6ea9"
        val input = "96.251.32.95"
        val hasher = HmacHasher(secret)

        // compute expected using standard javax.crypto.Mac to verify the result
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        val expectedBytes = mac.doFinal(input.toByteArray())
        val expectedHex = expectedBytes.joinToString("") { "%02x".format(it) }

        assertEquals(expectedHex, hasher.hash(input))
        // also ensure length is the 64-character hex string expected for SHA-256 HMAC
        assertEquals(64, hasher.hash(input).length)
    }

    @Test
    fun hmac_requires_non_blank_secret_throws_IllegalArgumentException() {
        val hasher = HmacHasher("")
        assertThrows(IllegalArgumentException::class.java) {
            hasher.hash("any-input")
        }
    }

    @Test
    fun hmac_with_invalid_algorithm_throws_exception_on_hash() {
        val invalidAlgoHasher = HmacHasher("secret", algorithm = "Invalid-Alg")
        assertThrows(Exception::class.java) {
            invalidAlgoHasher.hash("input")
        }
    }
}
