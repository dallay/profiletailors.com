package com.profiletailors.storage.infrastructure

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.s3.model.S3Exception

@OptIn(ExperimentalCoroutinesApi::class)
class S3RetryHelperTest {

    @Test
    fun \`should return result on first attempt when successful\`() = runTest {
        // When
        val result = S3RetryHelper.withRetry {
            "success"
        }

        // Then
        result shouldBe "success"
    }

    @Test
    fun \`should retry on transient status codes and eventually succeed\`() = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns 500
        every { mockS3Exception.message } returns "Internal Server Error"

        var attempts = 0

        // When
        val result = S3RetryHelper.withRetry {
            attempts++
            if (attempts < 3) {
                throw mockS3Exception
            }
            "success-after-retries"
        }

        // Then
        result shouldBe "success-after-retries"
        attempts shouldBe 3
    }

    @Test
    fun \`should throw last exception after exhausting retries\`() = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns 429
        every { mockS3Exception.message } returns "Too Many Requests"

        // When/Then
        try {
            S3RetryHelper.withRetry {
                throw mockS3Exception
            }
            error("Expected S3Exception to be thrown")
        } catch (exception: S3Exception) {
            exception shouldBe mockS3Exception
        }
    }

    @Test
    fun \`should fail immediately on non-transient status codes\`() = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns 404
        every { mockS3Exception.message } returns "Not Found"

        var attempts = 0

        // When/Then
        try {
            S3RetryHelper.withRetry {
                attempts++
                throw mockS3Exception
            }
            error("Expected S3Exception to be thrown")
        } catch (exception: S3Exception) {
            attempts shouldBe 1
        }
    }

    @Test
    fun \`should propagate non-S3 exceptions immediately\`() = runTest {
        // Given
        val error = RuntimeException("Other error")
        var attempts = 0

        // When/Then
        try {
            S3RetryHelper.withRetry {
                attempts++
                throw error
            }
            error("Expected RuntimeException to be thrown")
        } catch (exception: RuntimeException) {
            attempts shouldBe 1
        }
    }
}
