package com.profiletailors.spring.boot.config

import com.profiletailors.common.domain.security.Hasher
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class DataMaskingServiceTest {

    private val hasherRegistry = mockk<HasherRegistry>()
    private val service = DataMaskingService(hasherRegistry)

    @Test
    fun `should hash data using hasher from registry`() {
        // Given
        val data = "sensitive-data"
        val hashedData = "hashed-data"
        val hasher = mockk<Hasher>()

        every { hasherRegistry.get(null) } returns hasher
        every { hasher.hash(data) } returns hashedData

        // When
        val result = service.hashData(data)

        // Then
        result shouldBe hashedData
    }

    @Test
    fun `should hash data using specific hasher name`() {
        // Given
        val data = "sensitive-data"
        val hasherName = "sha256"
        val hashedData = "hashed-data"
        val hasher = mockk<Hasher>()

        every { hasherRegistry.get(hasherName) } returns hasher
        every { hasher.hash(data) } returns hashedData

        // When
        val result = service.hashData(data, hasherName)

        // Then
        result shouldBe hashedData
    }

    @Test
    fun `should propagate exception from hasher`() {
        // Given
        val data = "some-data"
        val hasher = mockk<Hasher>()
        every { hasherRegistry.get(null) } returns hasher
        every { hasher.hash(data) } throws RuntimeException("Hashing failed")

        // When/Then
        val ex = org.junit.jupiter.api.assertThrows<RuntimeException> {
            service.hashData(data)
        }
        ex.message shouldBe "Hashing failed"
    }

    @Test
    fun `should pass empty string to hasher`() {
        // Given
        val data = ""
        val hasher = mockk<Hasher>()
        every { hasherRegistry.get(null) } returns hasher
        every { hasher.hash(data) } returns "empty-hash"

        // When
        val result = service.hashData(data)

        // Then
        result shouldBe "empty-hash"
    }

    @Test
    fun `should use null hasher name by default`() {
        // Given
        val data = "data"
        val hasher = mockk<Hasher>()
        every { hasherRegistry.get(null) } returns hasher
        every { hasher.hash(data) } returns "result"

        // When
        service.hashData(data)

        // Then - verifies null hasher name used
        io.mockk.verify(exactly = 1) { hasherRegistry.get(null) }
    }
}
