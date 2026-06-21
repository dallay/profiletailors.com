package com.profiletailors.storage.infrastructure

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.awssdk.services.s3.model.S3Exception

@OptIn(ExperimentalCoroutinesApi::class)
class S3RetryHelperTest {

    @Test
    fun `should return result on first attempt when successful`() = runTest {
        // When
        val result = S3RetryHelper.withRetry {
            "success"
        }

        // Then
        result shouldBe "success"
    }

    @Test
    fun `should retry on 500 transient error and eventually succeed`() = runTest {
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
    fun `should throw last exception after exhausting all 3 retry attempts`() = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns 429
        every { mockS3Exception.message } returns "Too Many Requests"

        var attempts = 0

        // When/Then
        val exception = assertThrows<S3Exception> {
            S3RetryHelper.withRetry {
                attempts++
                throw mockS3Exception
            }
        }

        exception shouldBe mockS3Exception
        attempts shouldBe 3
    }

    @Test
    fun `should fail immediately on non-transient 404 status code`() = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns 404
        every { mockS3Exception.message } returns "Not Found"

        var attempts = 0

        // When/Then
        assertThrows<S3Exception> {
            S3RetryHelper.withRetry {
                attempts++
                throw mockS3Exception
            }
        }

        attempts shouldBe 1
    }

    @Test
    fun `should propagate non-S3 exceptions immediately without retrying`() = runTest {
        // Given
        val error = RuntimeException("Other error")
        var attempts = 0

        // When/Then
        assertThrows<RuntimeException> {
            S3RetryHelper.withRetry {
                attempts++
                throw error
            }
        }

        attempts shouldBe 1
    }

    @ParameterizedTest
    @ValueSource(ints = [409, 429, 500, 502, 503, 504])
    fun `should retry on all transient status codes`(statusCode: Int) = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns statusCode
        every { mockS3Exception.message } returns "Transient error"

        var attempts = 0

        // When
        val result = S3RetryHelper.withRetry {
            attempts++
            if (attempts < 2) {
                throw mockS3Exception
            }
            "recovered"
        }

        // Then
        result shouldBe "recovered"
        attempts shouldBe 2
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 405, 422])
    fun `should not retry on non-transient status codes`(statusCode: Int) = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns statusCode
        every { mockS3Exception.message } returns "Permanent error"

        var attempts = 0

        // When/Then
        assertThrows<S3Exception> {
            S3RetryHelper.withRetry {
                attempts++
                throw mockS3Exception
            }
        }

        attempts shouldBe 1
    }

    @Test
    fun `should succeed on second attempt after single transient failure`() = runTest {
        // Given
        val mockS3Exception = mockk<S3Exception>()
        every { mockS3Exception.statusCode() } returns 503
        every { mockS3Exception.message } returns "Service Unavailable"

        var attempts = 0

        // When
        val result = S3RetryHelper.withRetry {
            attempts++
            if (attempts == 1) throw mockS3Exception
            42
        }

        // Then
        result shouldBe 42
        attempts shouldBe 2
    }

    @Test
    fun `should not retry when operation succeeds immediately`() = runTest {
        // Given
        var attempts = 0

        // When
        S3RetryHelper.withRetry {
            attempts++
            Unit
        }

        // Then
        attempts shouldBe 1
    }
}
