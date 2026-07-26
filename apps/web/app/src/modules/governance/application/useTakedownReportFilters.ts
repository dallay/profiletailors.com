import { computed, ref, type Ref } from 'vue'
import type {
  TakedownReportResponse,
  TakedownReportStatus,
} from '@modules/governance/services/governance-api'

export function useTakedownReportFilters(reports: Ref<TakedownReportResponse[]>) {
  const statusFilter = ref<TakedownReportStatus | 'ALL'>('ALL')

  const filteredReports = computed(() => {
    if (statusFilter.value === 'ALL') {
      return reports.value
    }
    return reports.value.filter((r) => r.status === statusFilter.value)
  })

  const clearFilters = () => {
    statusFilter.value = 'ALL'
  }

  return {
    statusFilter,
    filteredReports,
    clearFilters,
  }
}
