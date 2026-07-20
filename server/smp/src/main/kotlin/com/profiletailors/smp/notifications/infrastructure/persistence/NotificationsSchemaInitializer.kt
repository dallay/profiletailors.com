package com.profiletailors.smp.notifications.infrastructure.persistence

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component

/**
 * Creates the `notifications` table on application startup.
 *
 * The notifications module is one of the few write paths in the system and currently
 * has no Flyway/Liquibase layer. A programmatic DDL is acceptable for early-stage
 * development — when migration tooling is added later, this code is removed and the
 * DDL lives in a versioned migration.
 */
@Component
internal class NotificationsSchemaInitializer(private val databaseClient: DatabaseClient) {

    private val log = LoggerFactory.getLogger(NotificationsSchemaInitializer::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun initializeSchema() {
        databaseClient.sql(CREATE_TABLE_SQL).fetch().rowsUpdated().block()
        databaseClient.sql(CREATE_INDEX_SQL).fetch().rowsUpdated().block()
        log.info("Notifications schema ready")
    }

    companion object {
        private const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS notifications (
                id VARCHAR(255) PRIMARY KEY,
                idempotency_key VARCHAR(500) UNIQUE NOT NULL,
                channel VARCHAR(50) NOT NULL,
                recipient VARCHAR(500) NOT NULL,
                template_id VARCHAR(255) NOT NULL,
                payload JSONB NOT NULL,
                status VARCHAR(50) NOT NULL,
                sent_at TIMESTAMP,
                failed_at TIMESTAMP,
                error_message TEXT,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
        """

        private const val CREATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_notifications_status
                ON notifications (status)
        """
    }
}
