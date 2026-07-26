import { ref, computed } from 'vue'

interface UsePaginationOptions {
  pageSize?: number
  initialPage?: number
}

export function usePagination<T>(items: T[] = [], options: UsePaginationOptions = {}) {
  const pageSize = ref(options.pageSize ?? 10)
  const currentPage = ref(options.initialPage ?? 1)

  const totalPages = computed(() => {
    return Math.ceil(items.length / pageSize.value)
  })

  const totalItems = computed(() => items.length)

  const paginatedItems = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    const end = start + pageSize.value
    return items.slice(start, end)
  })

  const hasNextPage = computed(() => currentPage.value < totalPages.value)
  const hasPrevPage = computed(() => currentPage.value > 1)

  const goToPage = (page: number) => {
    const targetPage = Math.max(1, Math.min(page, totalPages.value))
    currentPage.value = targetPage
  }

  const nextPage = () => {
    if (hasNextPage.value) {
      currentPage.value++
    }
  }

  const prevPage = () => {
    if (hasPrevPage.value) {
      currentPage.value--
    }
  }

  const setPageSize = (size: number) => {
    pageSize.value = Math.max(1, size)
    // Reset to page 1 when changing page size
    currentPage.value = 1
  }

  const reset = () => {
    currentPage.value = options.initialPage ?? 1
  }

  return {
    currentPage,
    pageSize,
    totalPages,
    totalItems,
    paginatedItems,
    hasNextPage,
    hasPrevPage,
    goToPage,
    nextPage,
    prevPage,
    setPageSize,
    reset,
  }
}
