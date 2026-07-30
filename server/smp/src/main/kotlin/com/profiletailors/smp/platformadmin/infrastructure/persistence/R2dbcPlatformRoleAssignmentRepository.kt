package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcPlatformRoleAssignmentRepository(private val databaseClient: DatabaseClient) :
    PlatformRoleAssignmentRepository {

    override suspend fun findById(id: PlatformRoleAssignmentId): PlatformRoleAssignment? =
        databaseClient.sql(SELECT_BY_ID)
            .bind("id", id.value)
            .map { row, _ -> row.toAssignment() }
            .one()
            .awaitSingleOrNull()

    override suspend fun findActiveByPrincipalId(principalId: UUID): List<PlatformRoleAssignment> =
        databaseClient.sql(SELECT_ACTIVE_BY_PRINCIPAL)
            .bind("principalId", principalId)
            .map { row, _ -> row.toAssignment() }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun findAllActive(): List<PlatformRoleAssignment> = databaseClient.sql(SELECT_ALL_ACTIVE)
        .map { row, _ -> row.toAssignment() }
        .all()
        .collectList()
        .awaitSingle()

    override suspend fun save(assignment: PlatformRoleAssignment): PlatformRoleAssignment {
        databaseClient.sql(INSERT)
            .bindAssignment(assignment)
            .then()
            .awaitSingleOrNull()
        return requireNotNull(findById(assignment.id))
    }

    override suspend fun update(assignment: PlatformRoleAssignment): PlatformRoleAssignment {
        val revokedAt = assignment.revokedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        databaseClient.sql(UPDATE)
            .bindNullable("revokedAt", revokedAt, OffsetDateTime::class.java)
            .bindNullable("revokedBy", assignment.revokedBy, UUID::class.java)
            .bind("version", assignment.version + 1)
            .bind("id", assignment.id.value)
            .bind("currentVersion", assignment.version)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return requireNotNull(findById(assignment.id))
    }

    private fun DatabaseClient.GenericExecuteSpec.bindAssignment(
        a: PlatformRoleAssignment,
    ): DatabaseClient.GenericExecuteSpec = bind("id", a.id.value)
        .bind("principalId", a.principalId)
        .bind("role", a.role.name)
        .bind("assignedAt", OffsetDateTime.ofInstant(a.assignedAt, ZoneOffset.UTC))
        .bind("assignedBy", a.assignedBy)
        .bindNullable(
            "revokedAt",
            a.revokedAt?.let {
                OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
            },
            OffsetDateTime::class.java,
        )
        .bindNullable("revokedBy", a.revokedBy, UUID::class.java)
        .bind("version", a.version)

    private fun Readable.toAssignment() = PlatformRoleAssignment(
        id = PlatformRoleAssignmentId(UUID.fromString(requireNotNull(get("id", String::class.java)))),
        principalId = requireNotNull(get("principal_id", UUID::class.java)),
        role = PlatformRole.valueOf(requireNotNull(get("role", String::class.java))),
        assignedAt = requireNotNull(get("assigned_at", OffsetDateTime::class.java)).toInstant(),
        assignedBy = requireNotNull(get("assigned_by", UUID::class.java)),
        revokedAt = get("revoked_at", OffsetDateTime::class.java)?.toInstant(),
        revokedBy = get("revoked_by", UUID::class.java),
        version = requireNotNull(get("version", Long::class.java)),
    )

    companion object {
        private const val SELECT_BY_ID = """
            SELECT id, principal_id, role, assigned_at, assigned_by, revoked_at, revoked_by, version
            FROM platform_role_assignments WHERE id = :id
        """
        private const val SELECT_ACTIVE_BY_PRINCIPAL = """
            SELECT id, principal_id, role, assigned_at, assigned_by, revoked_at, revoked_by, version
            FROM platform_role_assignments WHERE principal_id = :principalId AND revoked_at IS NULL
        """
        private const val SELECT_ALL_ACTIVE = """
            SELECT id, principal_id, role, assigned_at, assigned_by, revoked_at, revoked_by, version
            FROM platform_role_assignments WHERE revoked_at IS NULL
        """
        private const val INSERT = """
            INSERT INTO platform_role_assignments
              (id, principal_id, role, assigned_at, assigned_by, revoked_at, revoked_by, version)
            VALUES (:id, :principalId, :role, :assignedAt, :assignedBy, :revokedAt, :revokedBy, :version)
        """
        private const val UPDATE = """
            UPDATE platform_role_assignments
            SET revoked_at = :revokedAt, revoked_by = :revokedBy, version = :version
            WHERE id = :id AND version = :currentVersion
        """
    }
}

private fun <T> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, type)
