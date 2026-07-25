import { ref, computed } from 'vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

interface UsePublicationFiltersOptions {
  publications?: Publication[]
}

export function usePublicationFilters(options: UsePublicationFiltersOptions = {}) {
  const query = ref('')
  const statusFilter = ref<'all' | 'queued' | 'published' | 'cancelled'>('all')
  const channelIdFilter = ref<string | undefined>(undefined)
  const selectedSocialAccountId = ref<string | undefined>(undefined)

  const filterMatches = (
    pub: Publication,
    filters: {
      channel?: string
      socialAccountId?: string
      tag?: string
      postType?: string
    },
  ): boolean => {
    if (filters.channel && !(pub.channels as string[]).includes(filters.channel)) {
      return false
    }
    if (filters.socialAccountId && pub.accountId !== filters.socialAccountId) {
      return false
    }
    if (filters.tag && !pub.content.toLowerCase().includes(filters.tag.toLowerCase())) {
      return false
    }

    switch (filters.postType) {
      case 'queued':
        return pub.status === 'QUEUED'
      case 'published':
        return pub.status === 'PUBLISHED'
      case 'cancelled':
        return pub.status === 'CANCELLED'
      default:
        return true
    }
  }

  const isActive = computed(() => {
    return query.value !== '' || statusFilter.value !== 'all' || !!selectedSocialAccountId.value
  })

  const reset = () => {
    query.value = ''
    statusFilter.value = 'all'
    selectedSocialAccountId.value = undefined
  }

  return {
    query,
    statusFilter,
    channelIdFilter,
    selectedSocialAccountId,
    filterMatches,
    isActive,
    reset,
  }
}
