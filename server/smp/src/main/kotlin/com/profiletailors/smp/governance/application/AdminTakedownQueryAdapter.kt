package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus

@Service
internal class AdminTakedownQueryAdapter(
    private val repository: TakedownReportRepository,
    private val mediaAssetStatusReader: MediaAssetStatusReader,
) : AdminTakedownQueryPort {

    override suspend fun list(
        status: String?,
        workspaceId: String?,
        page: Int,
        size: Int,
    ): AdminTakedownPage<AdminTakedownReport> {
        val statusFilter = status?.let { TakedownReportStatus.valueOf(it) }
        val items = repository.findAll(statusFilter, workspaceId, page, size).map { it.toAdminReport() }
        val total = repository.count(statusFilter, workspaceId)
        return AdminTakedownPage.from(items, page, size, total)
    }

    override suspend fun get(reportId: String): AdminTakedownReportDetail {
        val report = repository.findByReportId(reportId)
            ?: throw AdminTakedownReportNotFoundException(reportId)
        val assetStatus = mediaAssetStatusReader.readStatus(report.workspaceId, report.assetId)
        return report.toAdminDetail(assetStatus)
    }
}
