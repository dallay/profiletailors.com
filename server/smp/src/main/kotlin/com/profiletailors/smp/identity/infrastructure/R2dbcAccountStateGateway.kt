package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.AccountStateGateway
import com.profiletailors.smp.identity.domain.UserAccountState
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcAccountStateGateway(private val databaseClient: DatabaseClient) : AccountStateGateway {
    override suspend fun findAccountState(principalId: String): UserAccountState? = databaseClient.sql(
        "SELECT account_state FROM principals WHERE id = :principalId AND principal_type = 'USER'",
    )
        .bind("principalId", principalId)
        .map { row, _ -> UserAccountState.valueOf(requireNotNull(row.get("account_state", String::class.java))) }
        .one()
        .awaitSingleOrNull()

    override suspend fun changeAccountState(
        principalId: String,
        expected: UserAccountState,
        replacement: UserAccountState,
    ): Boolean = databaseClient.sql(
        "UPDATE principals SET account_state = :replacement " +
            "WHERE id = :principalId AND principal_type = 'USER' AND account_state = :expected",
    )
        .bind("principalId", principalId)
        .bind("expected", expected.name)
        .bind("replacement", replacement.name)
        .fetch()
        .rowsUpdated()
        .awaitSingle() > 0
}
