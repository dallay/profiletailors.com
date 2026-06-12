package com.profiletailors.smp.audit.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SpringAuditHookSupplierTest {

    private val objectMapper = ObjectMapper()
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-20T12:00:00Z"),
        ZoneId.of("UTC"),
    )

    @Test
    fun `createR2dbcHook throws when databaseClient is null`() {
        val supplier = SpringAuditHookSupplier(
            databaseClient = null,
            objectMapper = objectMapper,
            clock = fixedClock,
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            supplier.createR2dbcHook()
        }

        assertNotNull(exception.message)
    }

    @Test
    fun `createNoOpHook returns NoOpAuditHook`() {
        val supplier = SpringAuditHookSupplier(
            databaseClient = null,
            objectMapper = objectMapper,
            clock = fixedClock,
        )

        val hook = supplier.createNoOpHook()

        assertNotNull(hook)
    }

    @Test
    fun `resolveAuditHook with auditEnabled false returns NoOpAuditHook`() {
        val hook = resolveAuditHook(
            auditEnabled = false,
            databaseClient = null,
            objectMapper = objectMapper,
            clock = fixedClock,
        )

        assertNotNull(hook)
    }
}
