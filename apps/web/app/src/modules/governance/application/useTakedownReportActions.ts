import { reactive, ref, type Ref } from 'vue'
import {
  approveTakedown,
  rejectTakedown,
  type TakedownReportResponse,
} from '@modules/governance/services/governance-api'

export function useTakedownReportActions(
  reports: Ref<TakedownReportResponse[]>,
  t: (key: string) => string,
) {
  const mutatingIds = reactive(new Set<string>())
  const error = ref<string | null>(null)

  const handleApprove = async (reportId: string) => {
    mutatingIds.add(reportId)
    error.value = null
    try {
      const updated = await approveTakedown(reportId)
      const idx = reports.value.findIndex((r) => r.reportId === reportId)
      if (idx !== -1) {
        reports.value[idx] = updated
      }
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : t('governance.takedown.review.errors.approveFailed')
    } finally {
      mutatingIds.delete(reportId)
    }
  }

  const handleReject = async (reportId: string, rejectionReason: string) => {
    if (!reportId || !rejectionReason.trim()) {
      return
    }
    mutatingIds.add(reportId)
    error.value = null
    try {
      const updated = await rejectTakedown(reportId, {
        rejectionReason: rejectionReason.trim(),
      })
      const idx = reports.value.findIndex((r) => r.reportId === reportId)
      if (idx !== -1) {
        reports.value[idx] = updated
      }
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : t('governance.takedown.review.errors.rejectFailed')
    } finally {
      mutatingIds.delete(reportId)
    }
  }

  const clearError = () => {
    error.value = null
  }

  return {
    mutatingIds,
    error,
    handleApprove,
    handleReject,
    clearError,
  }
}
