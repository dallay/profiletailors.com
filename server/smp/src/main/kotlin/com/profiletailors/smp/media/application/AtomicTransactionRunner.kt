package com.profiletailors.smp.media.application

/**
 * Domain port that runs an asynchronous operation inside an atomic database transaction.
 *
 * The application layer orchestrates business logic that must observe a consistent database
 * state across multiple repository calls (for example, reading a blob with `FOR UPDATE` and
 * then writing derived state). The infrastructure concern of how that atomicity is achieved
 * (R2DBC reactive transactions via `TransactionalOperator`, JDBC with `@Transactional`,
 * etc.) must not leak into the application layer.
 *
 * Implementations live in the infrastructure layer and bind the underlying transaction
 * manager's contract. From the application layer's perspective the only visible behaviour
 * is "either every write in `block` commits, or every write in `block` rolls back".
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