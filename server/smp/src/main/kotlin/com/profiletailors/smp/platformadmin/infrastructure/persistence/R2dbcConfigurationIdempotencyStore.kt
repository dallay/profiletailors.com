package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyRecord
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyStore
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class R2dbcConfigurationIdempotencyStore(private val databaseClient: DatabaseClient) : ConfigurationIdempotencyStore {
    override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? =
        databaseClient.sql(SELECT_RECORD)
            .bind(OPERATOR_PRINCIPAL_ID, operatorPrincipalId)
            .bind(IDEMPOTENCY_KEY, idempotencyKey)
            .map { row, _ ->
                ConfigurationIdempotencyRecord(
                    operatorPrincipalId = requireNotNull(row.get("operator_principal_id", UUID::class.java)),
                    operation = requireNotNull(row.get("command", String::class.java)),
                    idempotencyKey = requireNotNull(row.get("idempotency_key", String::class.java)),
                    responseJson = row.get("response_json", String::class.java),
                )
            }
            .one()
            .awaitSingleOrNull()

    override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean = try {
        databaseClient.sql(INSERT_RECORD)
            .bind(OPERATOR_PRINCIPAL_ID, record.operatorPrincipalId)
            .bind(COMMAND, record.operation)
            .bind(IDEMPOTENCY_KEY, record.idempotencyKey)
            .bindNull(RESPONSE_JSON, String::class.java)
            .fetch()
            .rowsUpdated()
            .awaitSingle() == 1L
    } catch (_: DuplicateKeyException) {
        false
    }

    override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) {
        databaseClient.sql(UPDATE_RESPONSE)
            .bind(OPERATOR_PRINCIPAL_ID, operatorPrincipalId)
            .bind(IDEMPOTENCY_KEY, idempotencyKey)
            .bind(RESPONSE_JSON, responseJson)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) {
        databaseClient.sql(DELETE_RECORD)
            .bind(OPERATOR_PRINCIPAL_ID, operatorPrincipalId)
            .bind(IDEMPOTENCY_KEY, idempotencyKey)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private companion object {
        const val OPERATOR_PRINCIPAL_ID = "operatorPrincipalId"
        const val IDEMPOTENCY_KEY = "idempotencyKey"
        const val COMMAND = "command"
        const val RESPONSE_JSON = "responseJson"
        const val SELECT_RECORD = """
            SELECT operator_principal_id, command, idempotency_key, response_json
            FROM platform_admin_configuration_idempotency
            WHERE operator_principal_id = :operatorPrincipalId AND idempotency_key = :idempotencyKey
        """
        const val INSERT_RECORD = """
            INSERT INTO platform_admin_configuration_idempotency
                (operator_principal_id, command, idempotency_key, response_json)
            VALUES (:operatorPrincipalId, :command, :idempotencyKey, :responseJson)
        """
        const val UPDATE_RESPONSE = """
            UPDATE platform_admin_configuration_idempotency
            SET response_json = :responseJson
            WHERE operator_principal_id = :operatorPrincipalId AND idempotency_key = :idempotencyKey
        """
        const val DELETE_RECORD = """
            DELETE FROM platform_admin_configuration_idempotency
            WHERE operator_principal_id = :operatorPrincipalId AND idempotency_key = :idempotencyKey
        """
    }
}
