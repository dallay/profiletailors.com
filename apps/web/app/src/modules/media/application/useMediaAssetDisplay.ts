import { computed } from 'vue'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'
import type { MediaAssetSummary, MediaStatus } from '@modules/media/services/media-api'

/**
 * Provides media type detection and URL resolution for asset display
 */
export function useMediaAssetDisplay(asset: MediaAssetSummary) {
  const isImage = computed(() => asset.mediaType.startsWith('image/'))
  const isVideo = computed(() => asset.mediaType.startsWith('video/'))
  const isPdf = computed(() => asset.mediaType === 'application/pdf')

  const previewUrl = computed(() => {
    const url = asset.previewUrl || asset.downloadUrl
    return url ? resolveApiUrl(url) : null
  })

  const downloadUrl = computed(() => {
    const url = asset.downloadUrl || asset.previewUrl
    return url ? resolveApiUrl(url) : null
  })

  const statusClass = computed(() => {
    const status = asset.status as MediaStatus
    if (status === 'PENDING_UPLOAD' || status === 'UPLOADING') {
      return 'border-text-display/30 bg-text-display/10 text-text-display'
    }
    switch (status) {
      case 'READY':
        return 'border-success/30 bg-success/10 text-success'
      case 'FAILED':
        return 'border-error/30 bg-error/10 text-error'
      case 'SUSPENDED':
        return 'border-warning/30 bg-warning/10 text-warning'
      default:
        return 'border-border-visible bg-bg-primary text-text-secondary'
    }
  })

  return {
    isImage,
    isVideo,
    isPdf,
    previewUrl,
    downloadUrl,
    statusClass,
  }
}
