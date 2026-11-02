package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.transaction.reactive.TransactionalOperator

class R2dbcPlatformAdminBootstrapTransactionRunner(private val transactionalOperator: TransactionalOperator) :
    AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T =
        transactionalOperator.transactional(mono { block() }).awaitSingle()
}
