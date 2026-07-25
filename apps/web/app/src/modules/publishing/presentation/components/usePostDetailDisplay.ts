import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

/**
 * Provides post status display formatting for detail modal
 */
export function usePostDetailDisplay(publication: Publication | null, locale: string) {
  const { t } = useI18n()

  const statusLabel = computed(() => {
    if (!publication) return ''
    switch (publication.status) {
      case 'DRAFT':
        return t('postDetail.status.draft')
      case 'QUEUED':
        return t('postDetail.status.queued')
      case 'SCHEDULED':
        return t('postDetail.status.scheduled')
      case 'PROCESSING':
        return t('postDetail.status.processing')
      case 'PUBLISHED':
        return t('postDetail.status.published')
      case 'BLOCKED':
        return t('postDetail.status.blocked')
      case 'FAILED':
        return t('postDetail.status.failed')
      case 'CANCELLED':
        return t('postDetail.status.cancelled')
      default:
        return publication.status
    }
  })

  const statusTone = computed(() => {
    if (!publication) return 'text-text-secondary'
    switch (publication.status) {
      case 'PUBLISHED':
        return 'text-success'
      case 'BLOCKED':
      case 'FAILED':
        return 'text-error'
      case 'CANCELLED':
        return 'text-text-secondary'
      case 'PROCESSING':
      case 'QUEUED':
      case 'SCHEDULED':
        return 'text-warning'
      default:
        return 'text-text-secondary'
    }
  })

  const viewPostUrl = computed(() => {
    if (!publication) return null
    if (publication.publicUrl) return publication.publicUrl
    const urn = publication.externalPublicationId
    if (urn?.startsWith('urn:li:')) {
      return `https://www.linkedin.com/feed/update/${encodeURIComponent(urn)}`
    }
    return null
  })

  const dateLocale = computed(() => (locale === 'es' ? 'es-ES' : 'en-US'))

  const scheduledAtLabel = computed(() => {
    if (!publication?.scheduledAt) return ''
    const d = new Date(publication.scheduledAt)
    return d.toLocaleString(dateLocale.value, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  })

  const publishedAtLabel = computed(() => {
    if (!publication?.publishedAt) return ''
    const d = new Date(publication.publishedAt)
    return d.toLocaleString(dateLocale.value, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  })

  return {
    statusLabel,
    statusTone,
    viewPostUrl,
    scheduledAtLabel,
    publishedAtLabel,
  }
}
