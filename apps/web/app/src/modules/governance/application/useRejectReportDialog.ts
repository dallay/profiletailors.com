import { computed, ref } from 'vue'

export function useRejectReportDialog() {
  const rejectReportId = ref<string | null>(null)
  const rejectionReason = ref('')

  const isOpen = computed(() => rejectReportId.value !== null)

  const openDialog = (reportId: string) => {
    rejectReportId.value = reportId
    rejectionReason.value = ''
  }

  const closeDialog = () => {
    rejectReportId.value = null
    rejectionReason.value = ''
  }

  const getReason = (): string => rejectionReason.value.trim()

  const isReasonEmpty = (): boolean => rejectionReason.value.trim().length === 0

  return {
    rejectReportId,
    rejectionReason,
    isOpen,
    openDialog,
    closeDialog,
    getReason,
    isReasonEmpty,
  }
}
