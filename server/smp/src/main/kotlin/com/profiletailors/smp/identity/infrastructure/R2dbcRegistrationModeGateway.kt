package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.identity.application.RegistrationModeChange
import com.profiletailors.smp.identity.application.RegistrationModeGateway
import com.profiletailors.smp.identity.application.RegistrationPolicy
import com.profiletailors.smp.identity.domain.RegistrationDecision
import com.profiletailors.smp.identity.domain.RegistrationMode
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcRegistrationModeGateway(
    private val databaseClient: DatabaseClient,
    private val properties: RegistrationConfigurationProperties,
) : RegistrationModeGateway {

    override suspend fun currentMode(): RegistrationMode = databaseClient.sql(
        "SELECT config_value FROM platform_operational_config WHERE config_key = :key",
    )
        .bind("key", REGISTRATION_MODE_KEY)
        .map { row, _ -> RegistrationMode.valueOf(requireNotNull(row.get("config_value", String::class.java))) }
        .one()
        .awaitSingleOrNull() ?: properties.mode

    override suspend fun changeMode(newMode: RegistrationMode): RegistrationModeChange {
        val change = databaseClient.sql(CHANGE_MODE_SQL)
            .bind("key", REGISTRATION_MODE_KEY)
            .bind("newValue", newMode.name)
            .map { row, _ ->
                RegistrationModeChange(
                    previousMode = RegistrationMode.valueOf(
                        requireNotNull(row.get("previous_value", String::class.java)),
                    ),
                    newMode = RegistrationMode.valueOf(
                        requireNotNull(row.get("new_value", String::class.java)),
                    ),
                )
            }
            .one()
            .awaitSingleOrNull()
        return checkNotNull(change) { "No persisted row for config key $REGISTRATION_MODE_KEY" }
    }

    companion object {
        private const val REGISTRATION_MODE_KEY = "registration.mode"
        private const val CHANGE_MODE_SQL = """
            WITH previous AS (
                SELECT config_value FROM platform_operational_config
                WHERE config_key = :key
                FOR UPDATE
            )
            UPDATE platform_operational_config AS updated
            SET config_value = :newValue, version = version + 1, updated_at = CURRENT_TIMESTAMP
            FROM previous
            WHERE updated.config_key = :key
            RETURNING previous.config_value AS previous_value, updated.config_value AS new_value
        """
    }
}

@Service
internal class DatabaseBackedRegistrationPolicy(private val gateway: RegistrationModeGateway) : RegistrationPolicy {
    override suspend fun evaluate(hasInvitationToken: Boolean): RegistrationDecision =
        gateway.currentMode().evaluate(hasInvitationToken)
}
