package com.profiletailors.common.domain.persistence

/**
 * Domain port that runs an asynchronous operation inside an atomic database transaction.
 *
 * The application layer orchestrates business logic that must observe a consistent database
 * state across multiple repository calls. The infrastructure concern of how that atomicity
 * is achieved must not leak into the application layer.
 */
interface AtomicTransactionRunner {
    /**
     * Executes [block] inside a single atomic transaction.
     *
     * On any uncaught exception inside [block] the transaction is rolled back and the
     * exception is re-thrown. On normal completion the transaction is committed.
     */
    suspend fun <T : Any> runAtomically(block: suspend () -> T): T
}
