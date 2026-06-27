package com.profiletailors.smp.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * Reactive transaction configuration for R2DBC.
 *
 * Declares the `R2dbcTransactionManager` (which Spring Data R2DBC can auto-configure when an
 * R2DBC `ConnectionFactory` bean is present, but declaring it explicitly here makes the
 * transactional contract unambiguous and removes surprises from auto-configuration drift)
 * and the programmatic `TransactionalOperator` that handlers use to wrap multi-statement
 * sequences that must commit or roll back atomically.
 *
 * Why programmatic and not `@Transactional`:
 * - The handlers use a custom `@Service` marker from `com.profiletailors.common.domain.Service`.
 * - Kotlin classes are `final` by default. Spring's CGLIB proxy needs `open` classes to apply
 *   declarative `@Transactional`. Keeping the handlers final for design reasons means we cannot
 *   rely on AOP-based declarative transactions.
 * - `TransactionalOperator.transactional {}` works without proxying and binds to the Reactor
 *   `Context` (subscription), not to a thread, which is the correct reactive model.
 */
@Configuration
class PersistenceConfig {

    @Bean
    fun r2dbcTransactionManager(connectionFactory: ConnectionFactory): R2dbcTransactionManager =
        R2dbcTransactionManager(connectionFactory)

    @Bean
    fun transactionalOperator(transactionManager: R2dbcTransactionManager): TransactionalOperator =
        TransactionalOperator.create(transactionManager)
}
