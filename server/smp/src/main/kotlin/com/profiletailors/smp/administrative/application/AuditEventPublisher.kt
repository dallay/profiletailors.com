package com.profiletailors.smp.administrative.application

import com.profiletailors.smp.administrative.domain.AdministrativeAuditEvent
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEventRepository

class AuditEventPublisher(private val repository: AdministrativeAuditEventRepository) {
    suspend fun publish(event: AdministrativeAuditEvent) {
        repository.save(event)
    }
}
