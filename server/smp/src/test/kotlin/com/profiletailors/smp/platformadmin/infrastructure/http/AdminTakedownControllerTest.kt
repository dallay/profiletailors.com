package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.governance.application.AdminTakedownReport
import com.profiletailors.smp.governance.application.AdminTakedownReportDetail
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyCodec
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyRecord
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyService
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.handler.AdminTakedownHandlers
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminTakedownControllerTest {

    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()
    private val handlers = mockk<AdminTakedownHandlers>()
    private val idempotencyStore = RecordingConfigurationIdempotencyStore()
    private val configurationIdempotencyService =
        ConfigurationIdempotencyService(idempotencyStore, RecordingConfigurationIdempotencyCodec())

    @Test
    fun `list returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/takedown-reports")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `list returns 403 with platform access denied`() {
        grantRoles(listOf(PlatformRole.SUPPORT_AGENT))
        coEvery { handlers.list(any(), any(), any(), any(), any()) } throws
            PlatformAccessDeniedException(PlatformPermission.GOVERNANCE_READ)

        webClient()
            .get()
            .uri("/api/admin/takedown-reports")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `list returns 200 paged result without workspace header`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))
        coEvery { handlers.list(any(), "REPORTED", "ws-a", 0, 25) } returns PagedResult.of(
            items = listOf(sampleReport()),
            page = 0,
            size = 25,
            totalElements = 1,
        )

        webClient()
            .get()
            .uri("/api/admin/takedown-reports?status=REPORTED&workspaceId=ws-a")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].reportId").isEqualTo("report-1")
            .jsonPath("$.items[0].reporterEmail").isEqualTo("reporter@example.com")
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(25)
            .jsonPath("$.totalElements").isEqualTo(1)
    }

    @Test
    fun `list enforces admin page max size`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .get()
            .uri("/api/admin/takedown-reports?size=500")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `detail returns 200 with asset status`() {
        grantRoles(listOf(PlatformRole.AUDITOR))
        coEvery { handlers.get(any(), "report-1") } returns sampleDetail()

        webClient()
            .get()
            .uri("/api/admin/takedown-reports/report-1")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.reportId").isEqualTo("report-1")
            .jsonPath("$.assetStatus").isEqualTo("READY")
            .jsonPath("$.reporterEmail").isEqualTo("reporter@example.com")
    }

    @Test
    fun `approve requires an idempotency key`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/takedown-reports/report-1/approve")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `approve returns 401 without principal context`() {
        webClient(principal = null)
            .post()
            .uri("/api/admin/takedown-reports/report-1/approve")
            .header("Idempotency-Key", "approve-key")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `reject requires an idempotency key`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/takedown-reports/report-1/reject")
            .header("Accept", "application/vnd.api.v1+json")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("rejectionReason" to "Not a violation"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `approve replays the same idempotency key without a second transition`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { handlers.approve(any()) } returns sampleReport(status = "APPROVED")

        webClient()
            .post()
            .uri("/api/admin/takedown-reports/report-1/approve")
            .header("Idempotency-Key", "approve-key")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED")

        webClient()
            .post()
            .uri("/api/admin/takedown-reports/report-1/approve")
            .header("Idempotency-Key", "approve-key")
            .header("Accept", "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED")

        coVerify(exactly = 1) { handlers.approve(any()) }
    }

    @Test
    fun `reject returns 200 on success`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))
        coEvery { handlers.reject(any()) } returns
            sampleReport(status = "DISMISSED", rejectionReason = "Not a violation")

        webClient()
            .post()
            .uri("/api/admin/takedown-reports/report-1/reject")
            .header("Idempotency-Key", "reject-key")
            .header("Accept", "application/vnd.api.v1+json")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("rejectionReason" to "Not a violation"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("DISMISSED")
            .jsonPath("$.rejectionReason").isEqualTo("Not a violation")
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminTakedownController(
                handlers = handlers,
                operatorAccessResolver = operatorAccessResolver,
                requestContextStore = FakeRequestContextStore(principal),
                configurationIdempotencyService = configurationIdempotencyService,
            ),
        )
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    private fun grantRoles(roles: List<PlatformRole>) {
        coEvery { operatorAccessResolver.resolve(any()) } returns OperatorAccess(operatorId, roles.toSet())
    }

    private fun operatorPrincipal() = PrincipalContext(
        principalId = operatorId.toString(),
        principalType = PrincipalType.USER,
        subject = "operator@example.com",
        provider = "jwt",
    )

    private class RecordingConfigurationIdempotencyStore : ConfigurationIdempotencyStore {
        private val records = mutableListOf<ConfigurationIdempotencyRecord>()

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? =
            records.firstOrNull { it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey }

        override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean {
            if (records.any {
                    it.operatorPrincipalId == record.operatorPrincipalId &&
                        it.idempotencyKey == record.idempotencyKey
                }
            ) {
                return false
            }
            records += record
            return true
        }

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) {
            val index = records.indexOfFirst {
                it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey
            }
            records[index] = records[index].copy(responseJson = responseJson)
        }

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) {
            records.removeIf { it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey }
        }
    }

    private class RecordingConfigurationIdempotencyCodec : ConfigurationIdempotencyCodec {
        private val values = mutableMapOf<String, Any>()

        override fun encode(value: Any): String {
            val json = "encoded-${values.size}"
            values[json] = value
            return json
        }

        override fun <T : Any> decode(responseJson: String, responseType: Class<T>): T {
            val value = values[responseJson] ?: error("Missing encoded value")
            return responseType.cast(value)
        }
    }

    private class FakeRequestContextStore(private val principal: PrincipalContext?) : RequestContextStore {
        override fun currentPrincipalContext(): PrincipalContext? = principal
        override fun setPrincipalContext(context: PrincipalContext?) = Unit
        override fun currentResourceContext(): ResourceContext? = null
        override fun setResourceContext(context: ResourceContext?) = Unit
        override fun currentRequestPath(): String? = null
        override fun setRequestPath(path: String?) = Unit
        override fun clear() = Unit
    }

    companion object {
        private fun sampleReport(status: String = "REPORTED", rejectionReason: String? = null) = AdminTakedownReport(
            reportId = "report-1",
            workspaceId = "ws-a",
            assetId = "asset-1",
            reportedById = "reporter-1",
            reason = "Copyright infringement",
            status = status,
            rejectionReason = rejectionReason,
            reviewedById = null,
            reviewedAt = null,
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.com/original",
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
        )

        private fun sampleDetail() = AdminTakedownReportDetail(
            reportId = "report-1",
            workspaceId = "ws-a",
            assetId = "asset-1",
            reportedById = "reporter-1",
            reason = "Copyright infringement",
            status = "REPORTED",
            rejectionReason = null,
            reviewedById = null,
            reviewedAt = null,
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.com/original",
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
            assetStatus = "READY",
        )
    }
}
