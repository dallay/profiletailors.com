import { computed } from 'vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import type { SchedulerStatus, SchedulerSurface } from '@modules/publishing/application/useCalendarUrl'

/**
 * Provides calendar header state derivation and filter management
 */
export function useCalendarHeaderState(
  calendarView: 'month' | 'week' | 'day',
  surface: SchedulerSurface,
  status: string,
  channelIds: string[],
) {
  const publishingStore = usePublishingStore()

  const calendarSurface = computed<SchedulerSurface>(() =>
    calendarView === 'month' ? 'calendar-month' : 'calendar-week',
  )

  const selectedChannel = computed(() => {
    const id = channelIds?.[0]
    if (!id) return null
    return publishingStore.channels.find((ch) => ch.accountId === id)
  })

  const statusIcon = computed(() => {
    switch (status) {
      case 'queued':
        return 'Clock'
      case 'published':
        return 'Check'
      case 'cancelled':
        return 'Ban'
      default:
        return 'Folder'
    }
  })

  return {
    calendarSurface,
    selectedChannel,
    statusIcon,
    channels: publishingStore.channels,
    hasNoChannels: publishingStore.hasNoChannels,
  }
}
