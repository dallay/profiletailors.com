package com.profiletailors.smp.audit.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.audit.application.AuditHookResolver
import com.profiletailors.smp.audit.application.AuditHookSupplier
import com.profiletailors.smp.audit.domain.AuditHook
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Clock

internal class SpringAuditHookSupplier(
    private val databaseClient: DatabaseClient?,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : AuditHookSupplier {
    override fun createR2dbcHook(): AuditHook {
        val client = databaseClient
            ?: throw IllegalStateException("Audit is enabled but DatabaseClient is not available")
        return R2dbcAuditHook(
            databaseClient = client,
            objectMapper = objectMapper,
            clock = clock,
        )
    }

    override fun createNoOpHook(): AuditHook = NoOpAuditHook()
}

internal fun resolveAuditHook(
    auditEnabled: Boolean,
    databaseClient: DatabaseClient?,
    objectMapper: ObjectMapper,
    clock: Clock,
): AuditHook = AuditHookResolver.resolve(
    auditEnabled = auditEnabled,
    supplier = SpringAuditHookSupplier(
        databaseClient = databaseClient,
        objectMapper = objectMapper,
        clock = clock,
    ),
)
