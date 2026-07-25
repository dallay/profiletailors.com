import { ref } from 'vue'

interface UseDeleteConfirmationOptions {
  onConfirm?: () => Promise<void> | void
  onCancel?: () => void
}

export function useDeleteConfirmation(options: UseDeleteConfirmationOptions = {}) {
  const isOpen = ref(false)
  const isDeleting = ref(false)
  const error = ref<string | null>(null)

  const open = () => {
    isOpen.value = true
    error.value = null
  }

  const close = () => {
    isOpen.value = false
    isDeleting.value = false
    error.value = null
    options.onCancel?.()
  }

  const confirm = async () => {
    isDeleting.value = true
    error.value = null

    try {
      await options.onConfirm?.()
      close()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'An error occurred'
    } finally {
      isDeleting.value = false
    }
  }

  return {
    isOpen,
    isDeleting,
    error,
    open,
    close,
    confirm,
  }
}
