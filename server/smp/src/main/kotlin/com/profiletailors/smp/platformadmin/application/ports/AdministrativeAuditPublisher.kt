package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent

interface AdministrativeAuditPublisher {
    suspend fun publish(event: AdminAuditEvent)
}
