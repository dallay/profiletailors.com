@file:Suppress("MaxLineLength", "WildcardImport", "StringShouldBeRawString", "TooManyFunctions", "LongMethod")

package com.profiletailors.smp.publishing.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.profiletailors.smp.publishing.domain.BulkImportJob
import com.profiletailors.smp.publishing.domain.BulkImportRow
import com.profiletailors.smp.publishing.domain.BulkJobStatus
import com.profiletailors.smp.publishing.domain.BulkRowStatus
import com.profiletailors.smp.publishing.domain.ImportError
import io.mockk.*
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.FetchSpec
import org.springframework.r2dbc.core.RowsFetchSpec
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.function.BiFunction

class R2dbcBulkImportJobRepositoryTest {

    private val fixedInstant = Instant.parse("2026-02-01T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
    private val objectMapper = ObjectMapper().registerModule(kotlinModule()).findAndRegisterModules()
    private val databaseClient = mockk<DatabaseClient>(relaxed = false)
    private val repository = R2dbcBulkImportJobRepository(databaseClient, objectMapper, fixedClock)

    @Test
    fun `findByIdempotencyKey returns job when present`() = runTest {
        val job = sampleJob()
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportJob>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        val slot = slot<BiFunction<Row, RowMetadata, BulkImportJob>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.one() } answers {
            val fn = slot.captured
            val readable = mockReadableForJob(job)
            @Suppress("UNCHECKED_CAST")
            Mono.just(fn.apply(readable, mockk<RowMetadata>()))
        }
        val result = repository.findByIdempotencyKey(job.idempotencyKey)
        assertNotNull(result)
        assertEquals(job.id, result!!.id)
    }

    @Test
    fun `findByIdempotencyKey returns null when missing`() = runTest {
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportJob>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        every { genericSpec.map(any<BiFunction<Row, RowMetadata, BulkImportJob>>()) } returns rowsSpec
        every { rowsSpec.one() } returns Mono.empty()
        assertNull(repository.findByIdempotencyKey("x".repeat(64)))
    }

    @Test
    fun `findByWorkspaceAndId returns job filtered`() = runTest {
        val job = sampleJob()
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportJob>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        val slot = slot<BiFunction<Row, RowMetadata, BulkImportJob>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.one() } answers {
            val fn = slot.captured
            val readable = mockReadableForJob(job)
            @Suppress("UNCHECKED_CAST")
            Mono.just(fn.apply(readable, mockk<RowMetadata>()))
        }
        val result = repository.findByWorkspaceAndId("workspace-1", job.id)
        assertNotNull(result)
    }

    @Test
    fun `findByWorkspaceAndId returns null when not found`() = runTest {
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportJob>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        every { genericSpec.map(any<BiFunction<Row, RowMetadata, BulkImportJob>>()) } returns rowsSpec
        every { rowsSpec.one() } returns Mono.empty()
        assertNull(repository.findByWorkspaceAndId("workspace-1", "missing"))
    }

    @Test
    fun `save inserts job`() = runTest {
        val job = sampleJob()
        val spec = mockSaveSpec()
        every { databaseClient.sql(ofType(String::class)) } returns spec
        val result = repository.save(job)
        assertEquals(job.id, result.id)
        verify { databaseClient.sql(match<String> { it.contains("INSERT INTO bulk_import_jobs") }) }
    }

    @Test
    fun `save throws IllegalStateException on duplicate`() = runTest {
        val job = sampleJob()
        val spec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(ofType(String::class)) } returns spec
        every { spec.fetch() } throws
            DataAccessResourceFailureException("duplicate key value violates unique constraint")
        assertThrows<IllegalStateException> { runTest { repository.save(job) } }
    }

    @Test
    fun `save rethrows non-duplicate DataAccessException`() = runTest {
        val job = sampleJob()
        val spec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(ofType(String::class)) } returns spec
        every { spec.fetch() } throws DataAccessResourceFailureException("connection refused")
        assertThrows<DataAccessResourceFailureException> { runTest { repository.save(job) } }
    }

    @Test
    fun `saveRows with empty list does not call database`() = runTest {
        repository.saveRows(emptyList())
        verify(exactly = 0) { databaseClient.sql(ofType(String::class)) }
    }

    @Test
    fun `saveRows inserts single chunk`() = runTest {
        val rows = listOf(sampleRow("bulk-job-1", 0, "brow-0"))
        val spec = mockSaveRowsSpec()
        every { databaseClient.sql(ofType(String::class)) } returns spec
        repository.saveRows(rows)
        verify { databaseClient.sql(match<String> { it.contains("INSERT INTO bulk_import_rows") }) }
    }

    @Test
    fun `saveRows chunked 100 splits 101 rows`() = kotlinx.coroutines.runBlocking {
        val rows = (0 until 101).map { idx -> sampleRow("bulk-job-1", idx, "brow-$idx") }
        val spec = mockSaveRowsSpec()
        every { databaseClient.sql(ofType(String::class)) } returns spec
        repository.saveRows(rows)
        verify(exactly = 2) { databaseClient.sql(ofType(String::class)) }
    }

    @Test
    fun `saveRows handles nullable fields and mediaUrls blank`() = runTest {
        val rows = listOf(
            BulkImportRow(
                id = "brow-null",
                jobId = "bulk-job-1",
                rowIndex = 0,
                status = BulkRowStatus.VALID,
                errors = emptyList(),
                publicationId = null,
                bodyText = null,
                scheduledFor = null,
                mediaUrls = emptyList(),
                hasConflict = false,
            ),
            BulkImportRow(
                id = "brow-media",
                jobId = "bulk-job-1",
                rowIndex = 1,
                status = BulkRowStatus.VALID,
                errors = emptyList(),
                bodyText = "hi",
                scheduledFor = fixedInstant,
                mediaUrls = listOf("https://a.com/x.png"),
                hasConflict = true,
            ),
        )
        val spec = mockSaveRowsSpec()
        every { databaseClient.sql(ofType(String::class)) } returns spec
        repository.saveRows(rows)
        verify { databaseClient.sql(ofType(String::class)) }
    }

    @Test
    fun `findRows returns ordered rows and parses mediaUrls and errors`() = runTest {
        val jobId = "bulk-job-1"
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportRow>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        val slot = slot<BiFunction<Row, RowMetadata, BulkImportRow>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.all().collectList() } answers {
            val fn = slot.captured
            val r0 = mockReadableForRow(jobId, 0, "brow-0", "[]", "", false, BulkRowStatus.VALID)
            val r1 =
                mockReadableForRow(
                    jobId,
                    1,
                    "brow-1",
                    "[{\"code\":\"ERR\",\"message\":\"bad\"}]",
                    " https://a.com/img.png , https://b.com/img.jpg ",
                    true,
                    BulkRowStatus.SCHEDULED,
                )
            Mono.just(listOf(fn.apply(r0, mockk()), fn.apply(r1, mockk())))
        }
        val result = repository.findRows(jobId)
        assertEquals(2, result.size)
        assertEquals(0, result[0].rowIndex)
        assertEquals(listOf("https://a.com/img.png", "https://b.com/img.jpg"), result[1].mediaUrls)
        assertEquals(true, result[1].hasConflict)
    }

    @Test
    fun `toBulkRow handles invalid json gracefully`() = runTest {
        val jobId = "bulk-job-1"
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportRow>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        val slot = slot<BiFunction<Row, RowMetadata, BulkImportRow>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.all().collectList() } answers {
            val fn = slot.captured
            val r = mockReadableForRow(jobId, 0, "brow-err", "not-json{{{", "", false, BulkRowStatus.FAILED)
            Mono.just(listOf(fn.apply(r, mockk())))
        }
        val result = repository.findRows(jobId)
        assertEquals(emptyList<ImportError>(), result[0].errors)
    }

    @Test
    fun `toBulkRow handles blank mediaUrls as empty`() = runTest {
        val jobId = "bulk-job-1"
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<BulkImportRow>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<String>()) } returns genericSpec
        val slot = slot<BiFunction<Row, RowMetadata, BulkImportRow>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.all().collectList() } answers {
            val fn = slot.captured
            val r = mockReadableForRow(jobId, 0, "brow-blank", "[]", "   ", false, BulkRowStatus.VALID)
            Mono.just(listOf(fn.apply(r, mockk())))
        }
        val result = repository.findRows(jobId)
        assertEquals(emptyList<String>(), result[0].mediaUrls)
    }

    private fun sampleJob(
        id: String = "bulk-job-1",
        workspaceId: String = "workspace-1",
        principalId: String = "principal-1",
        idempotencyKey: String = "b".repeat(64),
        csvHash: String = "c".repeat(64),
        status: BulkJobStatus = BulkJobStatus.PENDING,
        totalRows: Int = 2,
    ) = BulkImportJob(
        id = id,
        workspaceId = workspaceId,
        principalId = principalId,
        idempotencyKey = idempotencyKey,
        csvHash = csvHash,
        status = status,
        totalRows = totalRows,
        scheduledCount = 0,
        failedCount = 0,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
    )

    private fun sampleRow(jobId: String, rowIndex: Int, id: String) = BulkImportRow(
        id = id,
        jobId = jobId,
        rowIndex = rowIndex,
        status = BulkRowStatus.VALID,
        errors = emptyList(),
        bodyText = "row $rowIndex",
        mediaUrls = emptyList(),
        hasConflict = false,
    )

    private fun mockSaveSpec(): DatabaseClient.GenericExecuteSpec {
        val spec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { spec.bind(any<String>(), any<String>()) } returns spec
        every { spec.bind(any<String>(), any<Int>()) } returns spec
        every { spec.bind(any<String>(), any<Instant>()) } returns spec
        val fetchSpec = mockk<FetchSpec<Map<String, Any>>>(relaxed = false)
        every { spec.fetch() } returns fetchSpec
        every { fetchSpec.rowsUpdated() } returns Mono.just(1L)
        return spec
    }

    private fun mockSaveRowsSpec(): DatabaseClient.GenericExecuteSpec {
        val spec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { spec.bind(any<String>(), any<String>()) } returns spec
        every { spec.bind(any<String>(), any<Instant>()) } returns spec
        every { spec.bind(any<String>(), any<Boolean>()) } returns spec
        every { spec.bindNull(any<String>(), any<Class<String>>()) } returns spec
        every { spec.bindNull(any<String>(), any<Class<Instant>>()) } returns spec
        val fetchSpec = mockk<FetchSpec<Map<String, Any>>>(relaxed = false)
        every { spec.fetch() } returns fetchSpec
        every { fetchSpec.rowsUpdated() } returns Mono.just(1L)
        return spec
    }

    private fun mockReadableForJob(job: BulkImportJob): Row {
        val readable = mockk<Row>(relaxed = false)
        every { readable.get("id", String::class.java) } returns job.id
        every { readable.get("workspace_id", String::class.java) } returns job.workspaceId
        every { readable.get("principal_id", String::class.java) } returns job.principalId
        every { readable.get("idempotency_key", String::class.java) } returns job.idempotencyKey
        every { readable.get("csv_hash", String::class.java) } returns job.csvHash
        every { readable.get("status", String::class.java) } returns job.status.name
        every { readable.get("total_rows", Int::class.javaObjectType) } returns job.totalRows
        every { readable.get("scheduled_count", Int::class.javaObjectType) } returns job.scheduledCount
        every { readable.get("failed_count", Int::class.javaObjectType) } returns job.failedCount
        every { readable.get("created_at", OffsetDateTime::class.java) } returns
            OffsetDateTime.ofInstant(job.createdAt, ZoneId.of("UTC"))
        every { readable.get("updated_at", OffsetDateTime::class.java) } returns
            OffsetDateTime.ofInstant(job.updatedAt, ZoneId.of("UTC"))
        return readable
    }

    private fun mockReadableForRow(
        jobId: String,
        rowIndex: Int,
        id: String,
        errorsJson: String,
        mediaUrls: String,
        hasConflict: Boolean,
        status: BulkRowStatus,
    ): Row {
        val readable = mockk<Row>(relaxed = false)
        every { readable.get("id", String::class.java) } returns id
        every { readable.get("job_id", String::class.java) } returns jobId
        every { readable.get("row_index", Int::class.javaObjectType) } returns rowIndex
        every { readable.get("status", String::class.java) } returns status.name
        every { readable.get("errors", String::class.java) } returns errorsJson
        every { readable.get("media_urls", String::class.java) } returns mediaUrls
        every { readable.get("has_conflict", java.lang.Boolean::class.java) } returns
            (if (hasConflict) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE) as java.lang.Boolean?
        every { readable.get("publication_id", String::class.java) } returns null
        every { readable.get("body_text", String::class.java) } returns "row $rowIndex"
        every { readable.get("scheduled_for", OffsetDateTime::class.java) } returns null
        return readable
    }
}
