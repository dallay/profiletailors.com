package com.profiletailors.smp.media.infrastructure.persistence

import com.profiletailors.smp.media.application.AtomicTransactionRunner
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * R2DBC reactive-transaction implementation of [AtomicTransactionRunner].
 *
 * Binds the reactive transaction context to the Reactor subscription via `transactional {}`,
 * which is the correct reactive model (per Spring Framework 5.2+ reactive transaction
 * semantics). The repository calls inside [block] all share the same database connection
 * for the duration of the subscription, so row-level locks acquired with `SELECT ... FOR UPDATE`
 * remain held until the subscription terminates.
 */
@Component
class R2dbcAtomicTransactionRunner(private val transactionalOperator: TransactionalOperator) :
    AtomicTransactionRunner {

    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T =
        transactionalOperator.transactional(mono { block() }).awaitSingle()
}
