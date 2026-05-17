package com.profiletailors.spring.boot.infrastructure.persistence.outbox

import java.util.UUID
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface OutboxR2dbcRepository : CoroutineCrudRepository<OutboxEntity, UUID>
