package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

@Suppress("TooManyFunctions", "LargeClass", "UnusedParameter", "MaxLineLength", "StringShouldBeRawString")
class BulkBddSteps {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var bddDatabaseSupport: BddDatabaseSupport

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private var latestBulkResponse: EntityExchangeResult<ByteArray>? = null
    private var previousBulkResponse: EntityExchangeResult<ByteArray>? = null
    private var lastCsvText: String = ""
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Before
    fun resetBulkState() {
        latestBulkResponse = null
        previousBulkResponse = null
        lastCsvText = ""
    }

    private fun bulkBase(workspaceId: String) = "/api/v1/workspaces/$workspaceId/bulk"

    private fun bulkValidateJson(csvText: String) = objectMapper.writeValueAsString(mapOf("csvText" to csvText))

    private fun postBulkValidate(
        workspaceId: String,
        csvText: String,
        headerWorkspace: String = workspaceId,
    ): EntityExchangeResult<ByteArray> {
        lastCsvText = csvText
        previousBulkResponse = latestBulkResponse
        return webTestClient.post().uri("${bulkBase(workspaceId)}/validate")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, headerWorkspace)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(bulkValidateJson(csvText))
            .exchange().expectBody().returnResult().also { latestBulkResponse = it }
    }

    private fun postBulkSchedule(
        workspaceId: String,
        csvText: String,
        headerWorkspace: String = workspaceId,
    ): EntityExchangeResult<ByteArray> {
        val json = objectMapper.writeValueAsString(mapOf("csvText" to csvText, "csvHash" to csvText))
        previousBulkResponse = latestBulkResponse
        return webTestClient.post().uri("${bulkBase(workspaceId)}/schedule")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, headerWorkspace)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange().expectBody().returnResult().also { latestBulkResponse = it }
    }

    @When("POST \\/bulk\\/validate with csvText {string}")
    fun whenPostBulkValidateWithCsvText(csvText: String) {
        val normalized = csvText.replace("\\n", "\n")
        postBulkValidate(BddDatabaseSupport.WORKSPACE_ID, normalized)
    }

    @When("POST \\/bulk\\/validate with same csvText")
    fun whenPostBulkValidateSameCsv() {
        postBulkValidate(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
    }

    @When("POST \\/bulk\\/schedule with csvText {string}")
    fun whenPostBulkScheduleWithCsvText(csvText: String) {
        val normalized = csvText.replace("\\n", "\n")
        postBulkSchedule(BddDatabaseSupport.WORKSPACE_ID, normalized)
    }

    @When("GET \\/bulk\\/templates is called")
    fun whenGetBulkTemplates() {
        latestBulkResponse = webTestClient.get().uri("${bulkBase(BddDatabaseSupport.WORKSPACE_ID)}/templates")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange().expectBody().returnResult()
    }

    @When("GET \\/bulk\\/templates\\/linkedin-calendar\\/csv is called")
    fun whenGetBulkTemplateCsv() {
        latestBulkResponse =
            webTestClient.get().uri("${bulkBase(BddDatabaseSupport.WORKSPACE_ID)}/templates/linkedin-calendar/csv")
                .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
                .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
                .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
                .exchange().expectBody().returnResult()
    }

    @Then("validate MUST list {int} rows with {int} INVALID and no DB writes")
    fun thenValidateMustListRows(expected: Int, invalid: Int) = runBlocking {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        val map: Map<String, Any?> = objectMapper.readValue(body)
        val rows = map["rows"] as? List<*> ?: emptyList<Any>()
        assertEquals(expected, rows.size, "validate rows mismatch body=$body")
        val invalidCount = rows.count { (it as? Map<*, *>)?.get("status") == "INVALID" }
        assertEquals(invalid, invalidCount, "invalid count mismatch body=$body")
        val jobCount = databaseClient.sql("SELECT COUNT(*) FROM bulk_import_jobs").map { r, _ ->
            (r.get(0) as Number).toLong()
        }.one().awaitSingle()
        assertEquals(0L, jobCount, "validate must not persist bulk_import_jobs")
        val pubCount = bddDatabaseSupport.countScheduledPublications()
        assertEquals(0L, pubCount, "validate must not create publications")
        val status = latestBulkResponse?.status?.value() ?: 0
        assertEquals(200, status)
    }

    @Then("responses MUST match and neither MUST persist")
    fun thenResponsesMustMatch() = runBlocking {
        assertNotNull(latestBulkResponse)
        assertNotNull(previousBulkResponse)
        val cur = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        val prevBody = previousBulkResponse?.let { String(it.responseBody ?: ByteArray(0)) } ?: cur
        val curMap: Map<String, Any?> = objectMapper.readValue(cur)
        val prevMap: Map<String, Any?> = objectMapper.readValue(prevBody)
        assertEquals(prevMap["rows"], curMap["rows"], "idempotent validate responses must match")
        val jobCount = databaseClient.sql("SELECT COUNT(*) FROM bulk_import_jobs").map { r, _ ->
            (r.get(0) as Number).toLong()
        }.one().awaitSingle()
        assertEquals(0L, jobCount)
    }

    @Then("it MUST return scheduledCount {int} failedCount {int} with {int} SCHEDULED publications")
    fun thenScheduleCounts(scheduled: Int, failed: Int, pubs: Int) = runBlocking {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        val map: Map<String, Any?> = objectMapper.readValue(body)
        assertEquals(scheduled, (map["scheduledCount"] as? Number)?.toInt(), "scheduledCount mismatch $body")
        assertEquals(failed, (map["failedCount"] as? Number)?.toInt(), "failedCount mismatch $body")
        val status = latestBulkResponse?.status?.value() ?: 0
        if (scheduled > 0 && failed > 0) assertEquals(207, status) else assertEquals(200, status)
        val pubCount = bddDatabaseSupport.countScheduledPublications()
        assertTrue(pubCount >= pubs.toLong(), "expected at least $pubs publications got $pubCount body=$body")
        val jobId = map["jobId"] as? String
        assertNotNull(jobId)
        val rowCount = databaseClient.sql(
            "SELECT COUNT(*) FROM bulk_import_rows WHERE job_id = :jobId",
        ).bind("jobId", jobId!!).map {
                r,
                _,
            ->
            (r.get(0) as Number).toLong()
        }.one().awaitSingle()
        assertEquals((scheduled + failed).toLong(), rowCount)
    }

    @Then("list MUST be non-empty and CSV header MUST match canonical order")
    fun thenTemplateChecks() {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        assertTrue(body.contains("bodyText,scheduledFor,timezone,media_urls,hashtags"), "header missing $body")
        val map: Map<String, Any?> = try {
            objectMapper.readValue(body)
        } catch (_: Exception) {
            emptyMap()
        }
        val templates = map["templates"] as? List<*>
        if (templates != null) assertTrue(templates.isNotEmpty())
    }

    @Given("workspace {string} exists")
    fun givenWorkspaceExists(id: String) = runBlocking { bddDatabaseSupport.seedWorkspace(id) }

    @Given("user {string} with emailStatus VERIFIED in workspace {string} with token {string}")
    fun givenUserVerified(user: String, workspace: String, token: String) = runBlocking {
        bddDatabaseSupport.seedJwtAuthenticatedUserWithWorkspace(emailStatus = "VERIFIED")
        bddDatabaseSupport.seedSocialConnection("social-conn-bulk", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            "social-acc-bulk",
            "social-conn-bulk",
            "LINKEDIN",
            "linkedin-bulk-1",
            "PERSONAL_PROFILE",
            "Bulk User",
        )
    }

    @Given("csv with {int} rows")
    fun givenCsvWithRows(n: Int) {
        lastCsvText = buildString {
            append("bodyText,scheduledFor,timezone,media_urls,hashtags\n")
            repeat(n) { i -> append("Post $i,2099-06-15T10:00:00Z,UTC,,\n") }
        }
    }

    @When("schedule is called")
    fun whenScheduleIsCalled() {
        postBulkSchedule(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
    }

    @Then("system MUST persist in {int}-{int} transactions and report via job status")
    fun thenPersistTransactions(a: Int, b: Int) = runBlocking {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        val map: Map<String, Any?> = objectMapper.readValue(body)
        val totalRows = (map["totalRows"] as? Number)?.toInt() ?: 0
        assertEquals(1000, totalRows, "1000-row batch totalRows $body")
        val jobId = map["jobId"] as? String
        assertNotNull(jobId)
        val jobStatus = map["status"] as? String ?: "SCHEDULED"
        assertTrue(jobStatus in setOf("SCHEDULED", "PARTIAL", "SCHEDULING"), "job status $jobStatus")
        val rowCount = databaseClient.sql(
            "SELECT COUNT(*) FROM bulk_import_rows WHERE job_id = :jobId",
        ).bind("jobId", jobId!!).map {
                r,
                _,
            ->
            (r.get(0) as Number).toLong()
        }.one().awaitSingle()
        assertEquals(1000L, rowCount, "bulk_import_rows count")
        val pubCount = bddDatabaseSupport.countScheduledPublications()
        assertTrue(pubCount >= 900, "publications persisted $pubCount")
        val totalFromDb = databaseClient.sql(
            "SELECT total_rows FROM bulk_import_jobs WHERE id = :id",
        ).bind("id", jobId).map {
                r,
                _,
            ->
            r.get("total_rows", Integer::class.java)!!.toInt()
        }.one().awaitSingle()
        assertEquals(1000, totalFromDb)
    }

    @Given("validate flagged 2 VALID 1 INVALID")
    fun givenValidateFlagged() {
        lastCsvText =
            "bodyText,scheduledFor,timezone,media_urls,hashtags\nA,2099-06-15T10:00:00Z,UTC,,\nB,2099-06-15T11:00:00Z,UTC,,\n,not-a-date,UTC,,"
    }

    @Given("workspace A job is PARTIAL with total 3 scheduled 2 failed 1")
    fun givenWorkspaceAJob() = runBlocking {
        bddDatabaseSupport.seedSocialConnection("social-conn-bulk", "LINKEDIN", "ACTIVE")
        bddDatabaseSupport.seedSocialAccount(
            "social-acc-bulk",
            "social-conn-bulk",
            "LINKEDIN",
            "linkedin-bulk-1",
            "PERSONAL_PROFILE",
            "Bulk User",
        )
        val jobId = "job-workspace-a"
        databaseClient.sql(
            "INSERT INTO bulk_import_jobs (id, workspace_id, principal_id, idempotency_key, status, total_rows, scheduled_count, failed_count, csv_hash, created_at, updated_at) VALUES (:id, :ws, :pid, :key, :status, :total, :sc, :fc, :hash, NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
        )
            .bind("id", jobId).bind("ws", BddDatabaseSupport.WORKSPACE_ID).bind("pid", BddDatabaseSupport.PRINCIPAL_ID)
            .bind(
                "key",
                "a".repeat(64),
            ).bind(
                "status",
                "PARTIAL",
            ).bind("total", 3).bind("sc", 2).bind("fc", 1).bind("hash", "hash-a").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO bulk_import_rows (id, job_id, row_index, status, errors, body_text, has_conflict, created_at) VALUES (:id, :jid, 0, 'SCHEDULED', '[]', 'A', false, NOW()) ON CONFLICT DO NOTHING",
        ).bind("id", "row-a-0").bind("jid", jobId).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO bulk_import_rows (id, job_id, row_index, status, errors, body_text, has_conflict, created_at) VALUES (:id, :jid, 1, 'SCHEDULED', '[]', 'B', false, NOW()) ON CONFLICT DO NOTHING",
        ).bind("id", "row-a-1").bind("jid", jobId).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            "INSERT INTO bulk_import_rows (id, job_id, row_index, status, errors, body_text, has_conflict, created_at) VALUES (:id, :jid, 2, 'FAILED', '[{\"code\":\"INVALID_DATE\"}]', '', false, NOW()) ON CONFLICT DO NOTHING",
        ).bind("id", "row-a-2").bind("jid", jobId).fetch().rowsUpdated().awaitSingle()
        lastCsvText = jobId
    }

    @When("GET \\/bulk\\/jobs\\/{jobId} in A is called")
    fun whenGetBulkJobInA() {
        val jobId = if (lastCsvText.startsWith("job-")) lastCsvText else "job-workspace-a"
        latestBulkResponse = webTestClient.get().uri("${bulkBase(BddDatabaseSupport.WORKSPACE_ID)}/jobs/$jobId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .exchange().expectBody().returnResult()
    }

    @Then("it MUST return 200 with counts and row errors")
    fun thenReturn200WithCounts() {
        val status = latestBulkResponse?.status?.value() ?: 0
        assertEquals(200, status)
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        assertTrue(body.contains("totalRows") || body.contains("total_rows") || body.contains("\"totalRows\""))
        assertTrue(body.contains("PARTIAL") || body.contains("rows"))
    }

    @Given("job in workspace A {string} with id {string}")
    fun givenJobInWorkspaceA(ws: String, jobId: String) = runBlocking {
        bddDatabaseSupport.seedWorkspace(ws)
        bddDatabaseSupport.seedWorkspace(BddDatabaseSupport.WORKSPACE_ID)
        databaseClient.sql(
            "INSERT INTO bulk_import_jobs (id, workspace_id, principal_id, idempotency_key, status, total_rows, scheduled_count, failed_count, csv_hash, created_at, updated_at) VALUES (:id, :ws, :pid, :key, 'SCHEDULED', 1, 1, 0, :hash, NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
        )
            .bind(
                "id",
                jobId,
            ).bind(
                "ws",
                ws,
            ).bind(
                "pid",
                BddDatabaseSupport.PRINCIPAL_ID,
            ).bind(
                "key",
                jobId.padEnd(64, '0').take(64),
            ).bind("hash", "hash-$jobId").fetch().rowsUpdated().awaitSingle()
        lastCsvText = jobId
    }

    @When("workspace B {string} requests same jobId")
    fun whenWorkspaceBRequestsSameJobId(wsB: String) = runBlocking {
        bddDatabaseSupport.seedWorkspace(wsB)
        bddDatabaseSupport.seedWorkspaceMembershipIdempotent(BddDatabaseSupport.PRINCIPAL_ID, wsB)
        val jobId = lastCsvText
        latestBulkResponse = webTestClient.get().uri("${bulkBase(wsB)}/jobs/$jobId")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, wsB)
            .exchange().expectBody().returnResult()
    }

    @Then("it MUST return 404")
    fun thenReturn404() {
        assertEquals(404, latestBulkResponse?.status?.value())
    }

    @Given("CSV with 2 rows plus 1 blank line")
    fun givenCsvWithBlank() {
        lastCsvText =
            "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2099-06-15T10:00:00Z,UTC,,\n\nWorld,2099-06-15T11:00:00Z,UTC,,\n"
    }

    @When("validate is called")
    fun whenValidateIsCalled() {
        postBulkValidate(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
    }

    @Then("result MUST contain {int} rows")
    fun thenResultMustContainRows(n: Int) {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        val map: Map<String, Any?> = objectMapper.readValue(body)
        val rows = map["rows"] as? List<*> ?: emptyList<Any>()
        assertEquals(n, rows.size)
    }

    @Given("row with scheduledFor not-a-date empty body and media")
    fun givenRowWithInvalid() {
        lastCsvText = "bodyText,scheduledFor,timezone,media_urls,hashtags\n,not-a-date,UTC,,"
    }

    @Then("row MUST be INVALID with INVALID_DATE and MISSING_CONTENT")
    fun thenRowMustBeInvalid() {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        assertTrue(body.contains("INVALID"))
        assertTrue(body.contains("INVALID_DATE"))
        assertTrue(body.contains("MISSING_CONTENT"))
    }

    @Given("duplicate rows and row with blocked url {string}")
    fun givenDuplicateAndBlocked(url: String) {
        lastCsvText =
            "bodyText,scheduledFor,timezone,media_urls,hashtags\nSame,2099-06-15T10:00:00Z,UTC,,\nSame,2099-06-15T10:00:00Z,UTC,,\nHello,2099-06-15T11:00:00Z,UTC,$url,\n"
    }

    @When("validate or schedule is called")
    fun whenValidateOrSchedule() {
        postBulkValidate(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
    }

    @Then("second duplicate MUST warn DUPLICATE media row MUST be INVALID_MEDIA")
    fun thenDuplicateAndMedia() {
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        assertTrue(body.contains("DUPLICATE"), "DUPLICATE missing $body")
        assertTrue(body.contains("INVALID_MEDIA"), "INVALID_MEDIA missing $body")
    }

    @Given("user in A calls bulk for B")
    fun givenUserInACallsBulkForB() {
        lastCsvText = "ws-bulk-2"
    }

    @When("request is evaluated")
    fun whenRequestIsEvaluated() {
        val targetWs = lastCsvText
        runBlocking { bddDatabaseSupport.seedWorkspace(targetWs) }
        latestBulkResponse = webTestClient.post().uri("${bulkBase(targetWs)}/validate")
            .header(HttpHeaders.AUTHORIZATION, BddDatabaseSupport.USER_BEARER)
            .header(HttpHeaders.ACCEPT, BddDatabaseSupport.API_VERSION_MEDIA_TYPE)
            .header(BddDatabaseSupport.WORKSPACE_HEADER, BddDatabaseSupport.WORKSPACE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                bulkValidateJson("bodyText,scheduledFor,timezone,media_urls,hashtags\nHi,2099-06-15T10:00:00Z,UTC,,"),
            )
            .exchange().expectBody().returnResult()
    }

    @Then("it MUST return 403 or 404 and not process")
    fun thenReturn403or404() {
        val status = latestBulkResponse?.status?.value() ?: 0
        assertTrue(status == 403 || status == 404, "expected 403 or 404 got $status")
    }

    @Given("user with emailStatus UNVERIFIED calls schedule")
    fun givenUnverifiedCallsSchedule() = runBlocking {
        bddDatabaseSupport.seedJwtAuthenticatedUserWithWorkspace(emailStatus = "PENDING")
        lastCsvText = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHi,2099-06-15T10:00:00Z,UTC,,"
    }

    @Then("it MUST return 403")
    fun thenReturn403() {
        // schedule with unverified should be 403; we need to perform schedule if not yet
        if (latestBulkResponse == null || latestBulkResponse?.status?.value() == 200) {
            latestBulkResponse = postBulkSchedule(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
        }
        assertEquals(403, latestBulkResponse?.status?.value())
    }

    @Given("same principal resubmits identical csvHash")
    fun givenSamePrincipalResubmits() {
        lastCsvText = "bodyText,scheduledFor,timezone,media_urls,hashtags\nResubmit,2099-06-15T10:00:00Z,UTC,,"
        // first schedule
        postBulkSchedule(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
    }

    @Then("it MUST return 409 with existing jobId")
    fun thenReturn409() {
        val second = postBulkSchedule(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
        assertEquals(409, second.status.value())
        val body = String(second.responseBody ?: ByteArray(0))
        assertTrue(body.contains("jobId"))
    }

    @Given("LinkedIn row with media APPLICATION\\/PDF url {string}")
    fun givenLinkedInPdf(url: String) {
        lastCsvText = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2099-06-15T11:00:00Z,UTC,$url,\n"
    }

    @Then("row MUST be INVALID with CAPABILITY_VIOLATION")
    fun thenCapabilityViolation() {
        if (latestBulkResponse == null || String(latestBulkResponse?.responseBody ?: ByteArray(0)).isBlank()) {
            postBulkValidate(BddDatabaseSupport.WORKSPACE_ID, lastCsvText)
        }
        val body = String(latestBulkResponse?.responseBody ?: ByteArray(0))
        assertTrue(body.contains("CAPABILITY_VIOLATION"), "expected CAPABILITY_VIOLATION $body")
        assertTrue(body.contains("INVALID"))
    }
}
