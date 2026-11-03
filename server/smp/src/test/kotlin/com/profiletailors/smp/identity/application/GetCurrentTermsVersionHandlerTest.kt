package com.profiletailors.smp.identity.application

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetCurrentTermsVersionHandlerTest {
    @Test
    fun `returns the identity-owned current terms version`() = runTest {
        val result = GetCurrentTermsVersionHandler(IdentityCurrentTermsVersionProvider())
            .handle(GetCurrentTermsVersionQuery())

        assertEquals("terms-v1.0.0", result.version)
    }
}
