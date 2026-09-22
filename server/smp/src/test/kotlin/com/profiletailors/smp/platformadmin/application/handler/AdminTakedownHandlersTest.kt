package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.governance.application.AdminTakedownCommandPort
import com.profiletailors.smp.governance.application.AdminTakedownNotReviewableException
import com.profiletailors.smp.governance.application.AdminTakedownPage
import com.profiletailors.smp.governance.application.AdminTakedownQueryPort
import com.profiletailors.smp.governance.application.AdminTakedownReport
import com.profiletailors.smp.governance.application.AdminTakedownReportDetail
import com.profiletailors.smp.governance.application.AdminTakedownReportNotFoundException
import com.profiletailors.smp.platformadmin.application.command.ApproveAdminTakedownCommand
import com.profiletailors.smp.platformadmin.application.command.RejectAdminTakedownCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AdminTakedownHandlersTest {
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val clock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `owner operator and auditor can list reports`() = runTest {
        val query = FakeAdminTakedownQueryPort()
        val handlers = handlers(query = query)

        for (role in listOf(PlatformRole.PLATFORM_OWNER, PlatformRole.PLATFORM_OPERATOR, PlatformRole.AUDITOR)) {
            val page = handlers.list(setOf(role), status = "REPORTED", workspaceId = "ws-a", page = 0, size = 25)
            assertEquals(1, page.items.size)
            assertEquals("reporter@example.com", page.items.single().reporterEmail)
        }
        assertEquals(3, query.listCalls)
    }

    @Test
    fun `support agent list is denied without loading reporter email`() = runTest {
        val query = FakeAdminTakedownQueryPort()
        val handlers = handlers(query = query)

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.list(setOf(PlatformRole.SUPPORT_AGENT), null, null, 0, 25)
            }
        }
        assertEquals(0, query.listCalls)
        assertEquals(0, query.getCalls)
    }

    @Test
    fun `auditor mutate is denied with rejected audit ids only`() = runTest {
        val query = FakeAdminTakedownQueryPort()
        val command = FakeAdminTakedownCommandPort()
        val audit = RecordingAuditPublisher()
        val handlers = handlers(query, command, audit)

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.approve(
                    ApproveAdminTakedownCommand(operatorId, setOf(PlatformRole.AUDITOR), "report-1"),
                )
            }
        }

        assertEquals(0, command.approveCalls)
        val event = audit.events.single()
        assertEquals(AdminAuditAction.TAKEDOWN_APPROVED, event.action)
        assertEquals(AdminAuditResult.REJECTED, event.result)
        assertEquals("TAKEDOWN_REPORT", event.targetType)
        assertEquals("report-1", event.targetId)
        assertEquals("report-1", event.metadata["reportId"])
        assertFalse(event.metadata.values.any { it.contains("@") })
        assertFalse(event.metadata.keys.any { it.contains("email", ignoreCase = true) })
    }

    @Test
    fun `successful approve publishes admin audit after command port succeeds`() = runTest {
        val command = FakeAdminTakedownCommandPort()
        val audit = RecordingAuditPublisher()
        val handlers = handlers(command = command, audit = audit)

        val result = handlers.approve(
            ApproveAdminTakedownCommand(operatorId, setOf(PlatformRole.PLATFORM_OWNER), "report-1"),
        )

        assertEquals("APPROVED", result.status)
        assertEquals(1, command.approveCalls)
        val event = audit.events.single()
        assertEquals(AdminAuditAction.TAKEDOWN_APPROVED, event.action)
        assertEquals(AdminAuditResult.SUCCEEDED, event.result)
        assertEquals(
            mapOf(
                "reportId" to "report-1",
                "workspaceId" to "ws-a",
                "assetId" to "asset-1",
            ),
            event.metadata,
        )
        assertFalse(AdminAuditAction.entries.any { it.name.startsWith("MEDIA_TAKEDOWN") })
    }

    @Test
    fun `successful reject publishes admin audit after command port succeeds`() = runTest {
        val command = FakeAdminTakedownCommandPort()
        val audit = RecordingAuditPublisher()
        val handlers = handlers(command = command, audit = audit)

        val result = handlers.reject(
            RejectAdminTakedownCommand(
                operatorId,
                setOf(PlatformRole.PLATFORM_OPERATOR),
                "report-1",
                "Not a violation",
            ),
        )

        assertEquals("DISMISSED", result.status)
        assertEquals("Not a violation", result.rejectionReason)
        assertEquals(1, command.rejectCalls)
        val event = audit.events.single()
        assertEquals(AdminAuditAction.TAKEDOWN_REJECTED, event.action)
        assertEquals(AdminAuditResult.SUCCEEDED, event.result)
        assertEquals("report-1", event.metadata["reportId"])
        assertEquals("ws-a", event.metadata["workspaceId"])
        assertEquals("asset-1", event.metadata["assetId"])
    }

    @Test
    fun `not reviewable mutate publishes failed audit and does not transition twice`() = runTest {
        val command = FakeAdminTakedownCommandPort(notReviewable = true)
        val audit = RecordingAuditPublisher()
        val handlers = handlers(command = command, audit = audit)

        assertThrows(AdminTakedownNotReviewableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.approve(
                    ApproveAdminTakedownCommand(operatorId, setOf(PlatformRole.PLATFORM_OWNER), "report-1"),
                )
            }
        }

        assertEquals(1, command.approveCalls)
        val event = audit.events.single()
        assertEquals(AdminAuditAction.TAKEDOWN_APPROVED, event.action)
        assertEquals(AdminAuditResult.FAILED, event.result)
        assertEquals("report-1", event.metadata["reportId"])
        assertFalse(event.metadata.values.any { it.contains("@") })
    }

    @Test
    fun `missing report on get does not use IllegalArgumentException`() = runTest {
        val query = FakeAdminTakedownQueryPort(missing = true)
        val handlers = handlers(query = query)

        val thrown = assertThrows(AdminTakedownReportNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.get(setOf(PlatformRole.PLATFORM_OWNER), "missing")
            }
        }
        assertEquals("missing", thrown.reportId)
        assertEquals("AdminTakedownReportNotFoundException", thrown::class.simpleName)
    }

    private fun handlers(
        query: FakeAdminTakedownQueryPort = FakeAdminTakedownQueryPort(),
        command: FakeAdminTakedownCommandPort = FakeAdminTakedownCommandPort(),
        audit: RecordingAuditPublisher = RecordingAuditPublisher(),
    ) = AdminTakedownHandlers(
        queryPort = query,
        commandPort = command,
        auditPublisher = audit,
        clock = clock,
    )

    private class FakeAdminTakedownQueryPort(private val missing: Boolean = false) : AdminTakedownQueryPort {
        var listCalls = 0
            private set
        var getCalls = 0
            private set

        override suspend fun list(
            status: String?,
            workspaceId: String?,
            page: Int,
            size: Int,
        ): AdminTakedownPage<AdminTakedownReport> {
            listCalls += 1
            return AdminTakedownPage.from(listOf(sampleReport()), page, size, 1)
        }

        override suspend fun get(reportId: String): AdminTakedownReportDetail {
            getCalls += 1
            if (missing) throw AdminTakedownReportNotFoundException(reportId)
            return sampleDetail(reportId)
        }
    }

    private class FakeAdminTakedownCommandPort(private val notReviewable: Boolean = false) : AdminTakedownCommandPort {
        var approveCalls = 0
            private set
        var rejectCalls = 0
            private set

        override suspend fun approve(reportId: String, operatorId: String): AdminTakedownReport {
            approveCalls += 1
            if (notReviewable) throw AdminTakedownNotReviewableException(reportId, "APPROVED")
            return sampleReport(reportId = reportId, status = "APPROVED", reviewedById = operatorId)
        }

        override suspend fun reject(
            reportId: String,
            operatorId: String,
            rejectionReason: String,
        ): AdminTakedownReport {
            rejectCalls += 1
            if (notReviewable) throw AdminTakedownNotReviewableException(reportId, "DISMISSED")
            return sampleReport(
                reportId = reportId,
                status = "DISMISSED",
                rejectionReason = rejectionReason,
                reviewedById = operatorId,
            )
        }
    }

    private class RecordingAuditPublisher : AdministrativeAuditPublisher {
        val events = mutableListOf<AdminAuditEvent>()

        override suspend fun publish(event: AdminAuditEvent) {
            events += event
        }
    }

    companion object {
        private fun sampleReport(
            reportId: String = "report-1",
            status: String = "REPORTED",
            rejectionReason: String? = null,
            reviewedById: String? = null,
        ) = AdminTakedownReport(
            reportId = reportId,
            workspaceId = "ws-a",
            assetId = "asset-1",
            reportedById = "reporter-1",
            reason = "Copyright infringement",
            status = status,
            rejectionReason = rejectionReason,
            reviewedById = reviewedById,
            reviewedAt = reviewedById?.let { Instant.parse("2026-09-21T12:00:00Z") },
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.com/original",
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
        )

        private fun sampleDetail(reportId: String) = AdminTakedownReportDetail(
            reportId = reportId,
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
