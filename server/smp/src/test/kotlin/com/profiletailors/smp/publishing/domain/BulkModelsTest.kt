package com.profiletailors.smp.publishing.domain

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BulkModelsTest {

    @Test
    fun `creates bulk import job with required fields`() {
        val job = BulkImportJob(
            id = "job-1",
            workspaceId = "ws-1",
            principalId = "principal-1",
            idempotencyKey = "a".repeat(64),
            csvHash = "hash1",
            status = BulkJobStatus.SCHEDULING,
            totalRows = 10,
            scheduledCount = 0,
            failedCount = 0,
            createdAt = Instant.parse("2026-08-30T10:00:00Z"),
        )
        assertEquals("ws-1", job.workspaceId)
        assertEquals(BulkJobStatus.SCHEDULING, job.status)
    }

    @Test
    fun `requires workspace id`() {
        assertThrows<IllegalArgumentException> {
            BulkImportJob(
                id = "job-1",
                workspaceId = "",
                principalId = "principal-1",
                idempotencyKey = "a".repeat(64),
                csvHash = "hash",
                status = BulkJobStatus.PENDING,
                totalRows = 0,
                createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun `idempotency key must be sha256 hex`() {
        assertThrows<IllegalArgumentException> {
            BulkImportJob(
                id = "job-1",
                workspaceId = "ws-1",
                principalId = "p-1",
                idempotencyKey = "short",
                csvHash = "hash",
                status = BulkJobStatus.PENDING,
                totalRows = 0,
                createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun `creates bulk row with errors json`() {
        val row = BulkImportRow(
            id = "row-1",
            jobId = "job-1",
            rowIndex = 0,
            status = BulkRowStatus.VALID,
            errors = emptyList(),
        )
        assertEquals(0, row.rowIndex)
        assertTrue(row.errors.isEmpty())
    }

    @Test
    fun `import error codes are preserved`() {
        val error = ImportError(code = "INVALID_DATE", message = "bad date")
        assertEquals("INVALID_DATE", error.code)
        assertEquals("DUPLICATE", ImportError(code = "DUPLICATE", message = "dup").code)
    }

    @Test
    fun `bulk job transitions to partial`() {
        val job = BulkImportJob(
            id = "job-1",
            workspaceId = "ws-1",
            principalId = "p-1",
            idempotencyKey = "a".repeat(64),
            csvHash = "hash",
            status = BulkJobStatus.SCHEDULING,
            totalRows = 3,
            scheduledCount = 2,
            failedCount = 1,
            createdAt = Instant.now(),
        )
        val updated = job.withCounts(scheduledCount = 2, failedCount = 1)
        assertEquals(BulkJobStatus.PARTIAL, updated.status)
    }

    @Test
    fun `bulk template canonical header`() {
        assertEquals(
            "bodyText,scheduledFor,timezone,media_urls,hashtags",
            BulkTemplate.canonicalHeader(),
        )
    }

    @Test
    fun `sha256 idempotency helper is deterministic`() {
        val key1 = BulkImportJob.computeIdempotencyKey("ws-1", "p-1", "csvhash")
        val key2 = BulkImportJob.computeIdempotencyKey("ws-1", "p-1", "csvhash")
        assertEquals(key1, key2)
        assertEquals(64, key1.length)
        assertTrue(key1.matches(Regex("[a-f0-9]{64}")))
    }
}
