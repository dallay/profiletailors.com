import { ref } from 'vue'
import {
  listTakedownReports,
  type TakedownReportResponse,
} from '@modules/governance/services/governance-api'

export function useTakedownReportLoader(t: (key: string) => string) {
  const reports = ref<TakedownReportResponse[]>([])
  const isLoading = ref(true)
  const error = ref<string | null>(null)

  const loadReports = async () => {
    isLoading.value = true
    error.value = null
    try {
      reports.value = await listTakedownReports()
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : t('governance.takedown.review.errors.loadFailed')
    } finally {
      isLoading.value = false
    }
  }

  return {
    reports,
    isLoading,
    error,
    loadReports,
  }
}
