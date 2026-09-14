package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent

fun interface AdministrativeAuditPublisher {
    suspend fun publish(event: AdminAuditEvent)
}
