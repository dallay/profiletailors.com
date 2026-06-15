package com.profiletailors.storage

import com.profiletailors.storage.infrastructure.ProviderConfig
import com.profiletailors.storage.infrastructure.StorageAutoConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * Unit tests for [StorageAutoConfiguration]'s R2 wiring.
 *
 * These tests verify the credentials gap flagged in the second verify report:
 * the spec requires `accessKeyId` and `secretAccessKey` for R2 providers, and
 * the S3/S3Presigner builders MUST be configured with a `credentialsProvider`
 * that uses those credentials (R2 has no AWS credentials chain fallback).
 */
@DisplayName("StorageAutoConfiguration R2 wiring")
class StorageAutoConfigurationR2Test {

    companion object {
        private const val TEST_BUCKET = "media"
        private const val TEST_ACCOUNT_ID = "test-account-123"
        private const val TEST_ACCESS_KEY = "AKIAfakeTestKey"
        private const val TEST_SECRET_KEY = "fakeTestSecretKey"
    }

    private val config = StorageAutoConfiguration()

    @Nested
    @DisplayName("createR2Storage credentials validation")
    inner class CredentialsValidation {
        private val config: StorageAutoConfiguration = StorageAutoConfiguration()

        @Test
        fun `should throw IllegalArgumentException when accessKeyId is blank`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = "",
                secretAccessKey = TEST_SECRET_KEY,
            )

            val ex = assertThrows<IllegalArgumentException> {
                config.createR2Storage(providerConfig)
            }
            assertTrue(
                ex.message!!.contains("accessKeyId", ignoreCase = true),
                "Exception message should mention accessKeyId, got: ${ex.message}",
            )
        }

        @Test
        fun `should throw IllegalArgumentException when secretAccessKey is blank`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = TEST_ACCESS_KEY,
                secretAccessKey = "",
            )

            val ex = assertThrows<IllegalArgumentException> {
                config.createR2Storage(providerConfig)
            }
            assertTrue(
                ex.message!!.contains("secretAccessKey", ignoreCase = true),
                "Exception message should mention secretAccessKey, got: ${ex.message}",
            )
        }

        @Test
        fun `should throw IllegalArgumentException when both credentials are blank`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = "",
                secretAccessKey = "",
            )

            val ex = assertThrows<IllegalArgumentException> {
                config.createR2Storage(providerConfig)
            }
            assertTrue(
                ex.message!!.contains("R2 requires", ignoreCase = true),
                "Exception message should mention the requirement, got: ${ex.message}",
            )
        }

        @Test
        fun `should throw IllegalArgumentException when accessKeyId is missing`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = null,
                secretAccessKey = TEST_SECRET_KEY
            )

            val ex = assertThrows<IllegalArgumentException> {
                config.createR2Storage(providerConfig)
            }
            assertTrue(
                ex.message!!.contains("accessKeyId", ignoreCase = true),
                "Exception message should mention accessKeyId, got: ${ex.message}"
            )
        }

        @Test
        fun `should throw IllegalArgumentException when secretAccessKey is missing`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = TEST_ACCESS_KEY,
                secretAccessKey = null
            )

            val ex = assertThrows<IllegalArgumentException> {
                config.createR2Storage(providerConfig)
            }
            assertTrue(
                ex.message!!.contains("secretAccessKey", ignoreCase = true),
                "Exception message should mention secretAccessKey, got: ${ex.message}"
            )
        }

        @Test
        fun `should throw IllegalArgumentException when both credentials are missing`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = null,
                secretAccessKey = null
            )

            val ex = assertThrows<IllegalArgumentException> {
                config.createR2Storage(providerConfig)
            }
            assertTrue(
                ex.message!!.contains("credentials", ignoreCase = true) ||
                    ex.message!!.contains("accessKeyId", ignoreCase = true),
                "Exception message should mention credentials, got: ${ex.message}"
            )
        }
    }

    @Nested
    @DisplayName("createR2Storage credentials wiring")
    inner class CredentialsWiring {

        private val asyncBuilderMock = mockk<S3AsyncClientBuilder>(relaxed = true)
        private val presignerBuilderMock = mockk<S3Presigner.Builder>(relaxed = true)
        private val clientMock = mockk<S3AsyncClient>(relaxed = true)
        private val presignerMock = mockk<S3Presigner>(relaxed = true)

        @BeforeEach
        fun setupBuilders() {
            mockkStatic(S3AsyncClient::class)
            mockkStatic(S3Presigner::class)
            every { S3AsyncClient.builder() } returns asyncBuilderMock
            every { S3Presigner.builder() } returns presignerBuilderMock
            // Default chained calls: builders return themselves, build() returns mocks
            every { asyncBuilderMock.region(any()) } returns asyncBuilderMock
            every { asyncBuilderMock.endpointOverride(any()) } returns asyncBuilderMock
            every { asyncBuilderMock.forcePathStyle(any()) } returns asyncBuilderMock
            every { asyncBuilderMock.credentialsProvider(any()) } returns asyncBuilderMock
            every { asyncBuilderMock.build() } returns clientMock

            every { presignerBuilderMock.region(any()) } returns presignerBuilderMock
            every { presignerBuilderMock.endpointOverride(any()) } returns presignerBuilderMock
            every { presignerBuilderMock.credentialsProvider(any()) } returns presignerBuilderMock
            every { presignerBuilderMock.build() } returns presignerMock
        }

        @AfterEach
        fun teardownBuilders() {
            unmockkStatic(S3AsyncClient::class)
            unmockkStatic(S3Presigner::class)
        }

        @Test
        fun `should wire credentialsProvider on S3AsyncClient builder`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = TEST_ACCESS_KEY,
                secretAccessKey = TEST_SECRET_KEY
            )

            val storage = config.createR2Storage(providerConfig)

            // Return type is R2StorageAdapter; assert it builds without throwing.
            assertNotNull(storage)

            // Assert credentialsProvider was called on the S3AsyncClient builder
            verify(exactly = 1) { asyncBuilderMock.credentialsProvider(any<AwsCredentialsProvider>()) }
        }

        @Test
        fun `should wire credentialsProvider on S3Presigner builder`() {
            val providerConfig = ProviderConfig(
                type = "r2",
                bucket = TEST_BUCKET,
                accountId = TEST_ACCOUNT_ID,
                accessKeyId = TEST_ACCESS_KEY,
                secretAccessKey = TEST_SECRET_KEY
            )

            val storage = config.createR2Storage(providerConfig)

            // Return type is R2StorageAdapter; assert it builds without throwing.
            assertNotNull(storage)

            // Assert credentialsProvider was called on the S3Presigner builder
            verify(exactly = 1) { presignerBuilderMock.credentialsProvider(any<AwsCredentialsProvider>()) }
        }
    }
}
