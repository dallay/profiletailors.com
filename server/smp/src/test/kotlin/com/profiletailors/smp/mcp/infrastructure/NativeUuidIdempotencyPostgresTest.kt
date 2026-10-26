package com.profiletailors.smp.mcp.infrastructure

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.mcp.domain.IdempotencyRecord
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@Tag("postgres")
class NativeUuidIdempotencyPostgresTest : PostgresDatabaseTestBase() {
    @Container
    override val postgres = PostgresTestContainerSupport.newContainer("native_uuid_idempotency")

    @Test
    fun `record round trips with generated UUID key and native UUID scope`() = runTest {
        val workspaceId = UUID.randomUUID()
        val principalId = UUID.randomUUID()
        val keyHash = "a".repeat(64)
        val record = IdempotencyRecord(
            id = null,
            workspaceId = workspaceId.toString(),
            principalId = principalId.toString(),
            toolName = "publish",
            keyHash = keyHash,
            responseJson = "{}",
        )
        val repository = R2dbcIdempotencyRecordRepository(databaseClient)

        repository.save(record)

        assertEquals("{}", repository.find(record.workspaceId, record.principalId, record.toolName, keyHash))
        val storedId = databaseClient.sql(
            "SELECT id FROM idempotency_records WHERE workspace_id = :workspace AND principal_id = :principal",
        )
            .bind("workspace", workspaceId)
            .bind("principal", principalId)
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)) }
            .one().awaitSingle()
        assertNotNull(storedId)
        assertEquals(null, repository.find(UUID.randomUUID().toString(), record.principalId, "publish", keyHash))
        assertEquals(null, repository.find(record.workspaceId, UUID.randomUUID().toString(), "publish", keyHash))
    }
}
