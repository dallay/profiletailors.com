import { ref, computed } from 'vue'

export interface ApiError {
  status?: number
  message: string
  code?: string
  details?: Record<string, unknown>
}

function normalizeError(err: unknown): ApiError {
  if (err instanceof Error) {
    return {
      message: err.message,
      code: err.name,
    }
  }

  if (typeof err === 'object' && err !== null && 'status' in err && 'message' in err) {
    return err as ApiError
  }

  if (typeof err === 'string') {
    return { message: err }
  }

  return { message: 'An unknown error occurred' }
}

export function useApiError() {
  const error = ref<ApiError | null>(null)
  const isLoading = ref(false)

  const setError = (err: unknown) => {
    error.value = normalizeError(err)
  }

  const clearError = () => {
    error.value = null
  }

  const hasError = computed(() => !!error.value)

  const errorMessage = computed(() => error.value?.message ?? '')

  const isNetworkError = computed(() => {
    return error.value?.status === 0 || error.value?.code === 'NetworkError'
  })

  const isServerError = computed(() => {
    const status = error.value?.status
    return status !== undefined && status >= 500
  })

  const isClientError = computed(() => {
    const status = error.value?.status
    return status !== undefined && status >= 400 && status < 500
  })

  const is404 = computed(() => error.value?.status === 404)
  const is401 = computed(() => error.value?.status === 401)
  const is403 = computed(() => error.value?.status === 403)

  const retry = async <T,>(
    fn: () => Promise<T>,
    maxRetries: number = 3,
  ): Promise<T> => {
    let lastError: unknown

    for (let i = 0; i < maxRetries; i++) {
      try {
        isLoading.value = true
        clearError()
        return await fn()
      } catch (err) {
        lastError = err
        clearError()

        if (i < maxRetries - 1) {
          // Exponential backoff: 1s, 2s, 4s
          await new Promise(resolve => setTimeout(resolve, Math.pow(2, i) * 1000))
        }
      }
    }

    setError(lastError)
    throw normalizeError(lastError)
  }

  return {
    error,
    isLoading,
    hasError,
    errorMessage,
    isNetworkError,
    isServerError,
    isClientError,
    is404,
    is401,
    is403,
    setError,
    clearError,
    retry,
  }
}
