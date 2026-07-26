import { computed, ref, type Ref } from 'vue'
import type { MediaAssetSummary, MediaStatus } from '@modules/media/services/media-api'

export function useMediaLibraryFilters(assets: Ref<MediaAssetSummary[]>) {
  const searchQuery = ref('')
  const statusFilter = ref<'ALL' | 'READY' | 'PROCESSING' | 'FAILED' | 'SUSPENDED'>('ALL')
  const typeFilter = ref<'ALL' | 'IMAGE' | 'VIDEO' | 'PDF' | 'OTHER'>('ALL')
  const sortBy = ref<
    'newest' | 'oldest' | 'filename-asc' | 'filename-desc' | 'size-desc' | 'size-asc' | 'status'
  >('newest')

  const isProcessingStatus = (status: MediaStatus): boolean =>
    status === 'PENDING_UPLOAD' || status === 'UPLOADING'
  const isImage = (mediaType: string) => mediaType.startsWith('image/')
  const isVideo = (mediaType: string) => mediaType.startsWith('video/')
  const isPdf = (mediaType: string) => mediaType === 'application/pdf'

  const visibleAssets = computed(() => {
    const normalizedQuery = searchQuery.value.trim().toLowerCase()

    const filtered = assets.value.filter((asset) => {
      const matchesStatus =
        statusFilter.value === 'ALL' ||
        (statusFilter.value === 'PROCESSING'
          ? isProcessingStatus(asset.status)
          : asset.status === statusFilter.value)
      const matchesType =
        typeFilter.value === 'ALL' ||
        (typeFilter.value === 'IMAGE' && isImage(asset.mediaType)) ||
        (typeFilter.value === 'VIDEO' && isVideo(asset.mediaType)) ||
        (typeFilter.value === 'PDF' && isPdf(asset.mediaType)) ||
        (typeFilter.value === 'OTHER' &&
          !isImage(asset.mediaType) &&
          !isVideo(asset.mediaType) &&
          !isPdf(asset.mediaType))
      const matchesQuery =
        normalizedQuery.length === 0 ||
        asset.assetId.toLowerCase().includes(normalizedQuery) ||
        (asset.originalFilename ?? '').toLowerCase().includes(normalizedQuery) ||
        asset.mediaType.toLowerCase().includes(normalizedQuery)
      return matchesStatus && matchesType && matchesQuery
    })

    return [...filtered].sort((left, right) => {
      switch (sortBy.value) {
        case 'oldest':
          return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
        case 'filename-asc':
          return (left.originalFilename ?? left.assetId).localeCompare(
            right.originalFilename ?? right.assetId,
          )
        case 'filename-desc':
          return (right.originalFilename ?? right.assetId).localeCompare(
            left.originalFilename ?? left.assetId,
          )
        case 'size-asc':
          return (left.fileSizeBytes ?? 0) - (right.fileSizeBytes ?? 0)
        case 'size-desc':
          return (right.fileSizeBytes ?? 0) - (left.fileSizeBytes ?? 0)
        case 'status':
          return left.status.localeCompare(right.status)
        default:
          return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
      }
    })
  })

  const clearFilters = () => {
    searchQuery.value = ''
    statusFilter.value = 'ALL'
    typeFilter.value = 'ALL'
    sortBy.value = 'newest'
  }

  return {
    searchQuery,
    statusFilter,
    typeFilter,
    sortBy,
    visibleAssets,
    clearFilters,
  }
}
