import { ref, computed } from 'vue'

export interface UseModalStateOptions {
  onClose?: () => void
}

export function useModalState(options: UseModalStateOptions = {}) {
  const isOpen = ref(false)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const open = () => {
    isOpen.value = true
    error.value = null
  }

  const close = () => {
    isOpen.value = false
    options.onClose?.()
  }

  const setLoading = (loading: boolean) => {
    isLoading.value = loading
  }

  const setError = (err: string | null) => {
    error.value = err
  }

  const clearError = () => {
    error.value = null
  }

  const hasError = computed(() => !!error.value)

  return {
    isOpen,
    isLoading,
    error,
    hasError,
    open,
    close,
    setLoading,
    setError,
    clearError,
  }
}
