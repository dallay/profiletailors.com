package com.profiletailors.smp.credentials.application

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NoOpApiKeyCredentialStateLookupTest {

    @Test
    fun `should always throw not active exception`() = runTest {
        val lookup = NoOpApiKeyCredentialStateLookup()

        try {
            lookup.requireActive("any-key")
            throw AssertionError("Expected ApiKeyCredentialNotActiveException")
        } catch (e: ApiKeyCredentialNotActiveException) {
            assertThat(e).isInstanceOf(ApiKeyCredentialNotActiveException::class.java)
        }
    }
}
