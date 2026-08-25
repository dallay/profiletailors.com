package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordResetNotificationFailure
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailureRecorder
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.dao.DataAccessException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcPasswordResetNotificationFailureRepository(private val databaseClient: DatabaseClient) :
    PasswordResetNotificationFailureRecorder {
    /**
     * Records a password reset notification failure.
     *
     * @param failure The password reset notification failure to record.
     */
    override suspend fun record(failure: PasswordResetNotificationFailure) {
        try {
            databaseClient.sql(INSERT_SQL)
                .bind("id", UUID.randomUUID())
                .bind("principalId", failure.principalId)
                .bind("notificationType", failure.notificationType)
                .bind("attempts", failure.attempts)
                .bind("failedAt", OffsetDateTime.ofInstant(failure.failedAt, ZoneOffset.UTC))
                .bind("category", failure.category.name)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        } catch (cause: DataAccessException) {
            throw org.springframework.dao.DataAccessResourceFailureException(
                "Password reset notification failure persistence failed.",
                cause,
            )
        }
    }

    private companion object {
        const val INSERT_SQL = """
            INSERT INTO password_reset_notification_failures
                (id, principal_id, notification_type, attempts, failed_at, failure_category)
            VALUES
                (:id, :principalId, :notificationType, :attempts, :failedAt, :category)
        """
    }
}
