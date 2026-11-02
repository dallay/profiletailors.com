package com.profiletailors.smp.platformadmin.infrastructure.cli

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.IssueBootstrapInvitationHandler
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenGenerator
import com.profiletailors.smp.platformadmin.infrastructure.SecureBootstrapTokenGenerator
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcAdminAuditRepository
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcPlatformAdminBootstrapTransactionRunner
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcPlatformBootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcPlatformRoleAssignmentRepository
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class PlatformAdminBootstrapCliConfiguration {
    @Bean
    fun bootstrapConnectionFactory(
        @Value("\${SMP_R2DBC_URL}") url: String,
        @Value("\${SMP_DB_USERNAME}") username: String,
        @Value("\${SMP_DB_PASSWORD}") password: String,
    ): ConnectionFactory {
        val options = ConnectionFactoryOptions.parse(url)
            .mutate()
            .option(ConnectionFactoryOptions.USER, username)
            .option(ConnectionFactoryOptions.PASSWORD, password)
            .build()
        return ConnectionFactories.get(options)
    }

    @Bean
    fun bootstrapDatabaseClient(connectionFactory: ConnectionFactory): DatabaseClient =
        DatabaseClient.create(connectionFactory)

    @Bean
    fun bootstrapTransactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager =
        R2dbcTransactionManager(connectionFactory)

    @Bean
    fun bootstrapTransactionalOperator(transactionManager: ReactiveTransactionManager): TransactionalOperator =
        TransactionalOperator.create(transactionManager)

    @Bean
    fun bootstrapAtomicTransactionRunner(operator: TransactionalOperator): AtomicTransactionRunner =
        R2dbcPlatformAdminBootstrapTransactionRunner(operator)

    @Bean
    fun bootstrapInvitationRepository(databaseClient: DatabaseClient): BootstrapInvitationRepository =
        R2dbcPlatformBootstrapInvitationRepository(databaseClient)

    @Bean
    fun bootstrapRoleAssignmentRepository(databaseClient: DatabaseClient): PlatformRoleAssignmentRepository =
        R2dbcPlatformRoleAssignmentRepository(databaseClient)

    @Bean
    fun bootstrapAuditPublisher(databaseClient: DatabaseClient): AdministrativeAuditPublisher =
        R2dbcAdminAuditRepository(databaseClient)

    @Bean
    fun bootstrapIdentityLookup(databaseClient: DatabaseClient): PrincipalIdentityLookup =
        R2dbcPrincipalIdentityLookup(databaseClient)

    @Bean
    fun bootstrapTokenGenerator(): BootstrapTokenGenerator = SecureBootstrapTokenGenerator()

    @Bean
    fun bootstrapClock(): Clock = Clock.systemUTC()

    @Bean
    fun bootstrapInvitationIssuer(
        repository: BootstrapInvitationRepository,
        identityLookup: PrincipalIdentityLookup,
        auditPublisher: AdministrativeAuditPublisher,
        transactionRunner: AtomicTransactionRunner,
        tokenGenerator: BootstrapTokenGenerator,
        clock: Clock,
        @Value("\${SMP_ADMIN_PUBLIC_ORIGIN}") adminPublicOrigin: String,
    ): IssueBootstrapInvitationHandler = IssueBootstrapInvitationHandler(
        repository = repository,
        identityLookup = identityLookup,
        auditPublisher = auditPublisher,
        transactionRunner = transactionRunner,
        tokenGenerator = tokenGenerator,
        clock = clock,
        adminPublicOrigin = adminPublicOrigin,
    )
}
