package com.profiletailors.common.domain.persistence

/**
 * Domain port that runs an asynchronous operation inside an atomic database transaction.
 *
 * The application layer orchestrates business logic that must observe a consistent database
 * state across multiple repository calls. The infrastructure concern of how that atomicity
 * is achieved must not leak into the application layer.
 */
interface AtomicTransactionRunner {
    /** Rolls back on uncaught exceptions and commits when [block] completes normally. */
    suspend fun <T : Any> runAtomically(block: suspend () -> T): T

    companion object {
        /** For tests only. Executes [AtomicTransactionRunner.runAtomically] blocks without atomicity guarantees. */
        val noop: AtomicTransactionRunner = object : AtomicTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
        }
    }
}
