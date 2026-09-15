package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.identity.application.InvalidPrincipalStatusTransitionException
import com.profiletailors.smp.identity.application.PrincipalLifecycle
import com.profiletailors.smp.identity.application.PrincipalNotFoundException
import com.profiletailors.smp.identity.application.PrincipalStatusTransition
import com.profiletailors.smp.identity.application.PrincipalVersionConflictException
import com.profiletailors.smp.identity.domain.PrincipalStatus
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcPrincipalLifecycle(
    private val databaseClient: DatabaseClient,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
) : PrincipalLifecycle {

    override suspend fun deactivate(principalId: String, expectedVersion: Long): PrincipalStatusTransition {
        val current = findOrThrow(principalId)
        checkVersion(principalId, expectedVersion, current.version)
        if (current.status == PrincipalStatus.DEACTIVATED) return PrincipalStatusTransition.ALREADY_IN_TARGET_STATE
        if (current.status != PrincipalStatus.ACTIVE && current.status != PrincipalStatus.SUSPENDED) {
            throw InvalidPrincipalStatusTransitionException(principalId, current.status, PrincipalStatus.DEACTIVATED)
        }
        compareAndSwap(principalId, expectedVersion, PrincipalStatus.DEACTIVATED)
        return PrincipalStatusTransition.TRANSITIONED
    }

    override suspend fun reactivate(principalId: String, expectedVersion: Long): PrincipalStatusTransition {
        val current = findOrThrow(principalId)
        checkVersion(principalId, expectedVersion, current.version)
        if (current.status == PrincipalStatus.ACTIVE) return PrincipalStatusTransition.ALREADY_IN_TARGET_STATE
        if (current.status != PrincipalStatus.DEACTIVATED) {
            throw InvalidPrincipalStatusTransitionException(principalId, current.status, PrincipalStatus.ACTIVE)
        }
        compareAndSwap(principalId, expectedVersion, PrincipalStatus.ACTIVE)
        return PrincipalStatusTransition.TRANSITIONED
    }

    override suspend fun revokeSessions(principalId: String) {
        refreshSessionLifecycleService.revokeAllForPrincipal(principalId)
    }

    private suspend fun findOrThrow(principalId: String): PrincipalLifecycleSnapshot =
        databaseClient.sql("SELECT id, status, version FROM principals WHERE id = :id")
            .bind("id", principalId)
            .map { row, _ ->
                PrincipalLifecycleSnapshot(
                    status = PrincipalStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                    version = requireNotNull(row.get("version", Long::class.java)),
                )
            }
            .one()
            .awaitSingleOrNull()
            ?: throw PrincipalNotFoundException(principalId)

    private fun checkVersion(principalId: String, expectedVersion: Long, actualVersion: Long) {
        if (actualVersion != expectedVersion) {
            throw PrincipalVersionConflictException(principalId, expectedVersion, actualVersion)
        }
    }

    private suspend fun compareAndSwap(principalId: String, expectedVersion: Long, target: PrincipalStatus) {
        val updated = databaseClient.sql(
            "UPDATE principals SET status = :status, version = version + 1 " +
                "WHERE id = :id AND version = :expectedVersion",
        )
            .bind("id", principalId)
            .bind("status", target.name)
            .bind("expectedVersion", expectedVersion)
            .fetch()
            .rowsUpdated()
            .awaitSingleOrNull() ?: 0L
        if (updated == 0L) {
            val current = findOrThrow(principalId)
            throw PrincipalVersionConflictException(principalId, expectedVersion, current.version)
        }
    }

    private data class PrincipalLifecycleSnapshot(val status: PrincipalStatus, val version: Long)
}
