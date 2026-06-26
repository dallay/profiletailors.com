package com.profiletailors.smp.audit.application

import com.profiletailors.smp.audit.domain.AuditHook

interface AuditHookSupplier {
    fun createR2dbcHook(): AuditHook

    fun createNoOpHook(): AuditHook
}

object AuditHookResolver {
    fun resolve(auditEnabled: Boolean, supplier: AuditHookSupplier): AuditHook = if (auditEnabled) {
        supplier.createR2dbcHook()
    } else {
        supplier.createNoOpHook()
    }
}
