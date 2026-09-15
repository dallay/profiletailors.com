package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.platformadmin.application.contracts.PrincipalAdmin
import com.profiletailors.smp.platformadmin.application.contracts.PrincipalSummary
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcPrincipalAdmin(private val databaseClient: DatabaseClient) : PrincipalAdmin {

    override suspend fun findById(principalId: String): PrincipalSummary? = databaseClient.sql(
        """
            SELECT p.id, p.principal_type, COALESCE(p.status, 'ACTIVE') AS status
            FROM principals p
            WHERE p.id = :id
        """.trimIndent(),
    )
        .bind("id", principalId)
        .map { row, _ ->
            PrincipalSummary(
                principalId = requireNotNull(row.get("id", String::class.java)),
                principalType = requireNotNull(row.get("principal_type", String::class.java)),
                status = PrincipalStatus.valueOf(
                    requireNotNull(row.get("status", String::class.java)),
                ),
                version = 1L,
            )
        }
        .one()
        .awaitSingleOrNull()

    override suspend fun updateStatus(principalId: String, status: PrincipalStatus): Boolean {
        val rows = databaseClient.sql(
            """
            UPDATE principals
            SET status = :status
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", principalId)
            .bind("status", status.name)
            .fetch()
            .rowsUpdated()
            .awaitSingleOrNull() ?: 0
        return rows > 0
    }
}
