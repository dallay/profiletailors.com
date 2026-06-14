package com.profiletailors.common.domain.vo.ip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class IpHashTest {
    private val validHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"

    @Test
    fun `should create a valid IP hash`() {
        val ipHash = IpHash(validHash)
        assertEquals(validHash, ipHash.value)
    }

    @Test
    fun `should create with uppercase hex`() {
        val upperHash = "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2"
        val ipHash = IpHash(upperHash)
        assertEquals(upperHash, ipHash.value)
    }

    @Test
    fun `should throw exception for wrong length`() {
        val shortHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b"
        val longHash = validHash + "ff"

        assertThrows(IllegalArgumentException::class.java) {
            IpHash(shortHash)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IpHash(longHash)
        }
    }

    @Test
    fun `should throw exception for invalid characters`() {
        val withZ = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1bz"
        val withG = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1bg"

        assertThrows(IllegalArgumentException::class.java) {
            IpHash(withZ)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IpHash(withG)
        }
    }

    @Test
    fun `should create via from() factory`() {
        val ipHash = IpHash.from(validHash)
        assertEquals(validHash, ipHash.value)
    }

    @Test
    fun `compare two equal hashes`() {
        val hash1 = IpHash(validHash)
        val hash2 = IpHash(validHash)
        assertEquals(hash1, hash2)
        assertEquals(hash1.hashCode(), hash2.hashCode())
    }

    @Test
    fun `compare two different hashes`() {
        val differentHash = "f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5a1b2"
        val hash1 = IpHash(validHash)
        val hash2 = IpHash(differentHash)
        assertNotEquals(hash1, hash2)
        assertNotEquals(hash1.hashCode(), hash2.hashCode())
    }
}
