import { ref, computed } from 'vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

interface UsePublicationActionsOptions {
  onSuccess?: (action: 'delete' | 'edit' | 'reschedule') => void
  onError?: (error: unknown) => void
}

export function usePublicationActions(options: UsePublicationActionsOptions = {}) {
  const isDeleting = ref(false)
  const isRescheduleModalOpen = ref(false)
  const selectedPublication = ref<Publication | null>(null)
  const error = ref<string | null>(null)

  const canEdit = (publication: Publication | null): boolean => {
    if (!publication) return false
    return ['DRAFT', 'QUEUED', 'SCHEDULED'].includes(publication.status)
  }

  const canDelete = (publication: Publication | null): boolean => {
    if (!publication) return false
    return ['DRAFT', 'QUEUED', 'SCHEDULED'].includes(publication.status)
  }

  const canReschedule = (publication: Publication | null): boolean => {
    if (!publication) return false
    return ['QUEUED', 'SCHEDULED'].includes(publication.status)
  }

  const setSelectedPublication = (pub: Publication | null) => {
    selectedPublication.value = pub
    error.value = null
  }

  const openRescheduleModal = (pub: Publication) => {
    if (!canReschedule(pub)) return
    selectedPublication.value = pub
    isRescheduleModalOpen.value = true
  }

  const closeRescheduleModal = () => {
    isRescheduleModalOpen.value = false
    selectedPublication.value = null
  }

  const setDeleting = (state: boolean) => {
    isDeleting.value = state
  }

  const setError = (err: unknown) => {
    error.value = err instanceof Error ? err.message : String(err)
    options.onError?.(err)
  }

  const clearError = () => {
    error.value = null
  }

  const hasError = computed(() => !!error.value)

  return {
    isDeleting,
    isRescheduleModalOpen,
    selectedPublication,
    error,
    hasError,
    canEdit,
    canDelete,
    canReschedule,
    setSelectedPublication,
    openRescheduleModal,
    closeRescheduleModal,
    setDeleting,
    setError,
    clearError,
  }
}
