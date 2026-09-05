package com.profiletailors.smp.administrative.domain

import java.util.UUID

interface AdministrativeAuditEventRepository {
    suspend fun save(event: AdministrativeAuditEvent): AdministrativeAuditEvent

    suspend fun findById(id: UUID): AdministrativeAuditEvent?

    suspend fun findByActor(actorId: UUID): List<AdministrativeAuditEvent>

    suspend fun findByTarget(targetType: String, targetId: String): List<AdministrativeAuditEvent>

    suspend fun findByCorrelationId(correlationId: String): List<AdministrativeAuditEvent>
}
