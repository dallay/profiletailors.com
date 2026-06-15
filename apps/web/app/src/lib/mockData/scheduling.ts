import type { ScheduleItem, PostingTimeSlot } from '../types/dashboard'

// ---------------------------------------------------------------------------
// Upcoming Schedule
// ---------------------------------------------------------------------------

export const upcomingSchedule: ScheduleItem[] = [
  {
    id: 'sch-1',
    title: 'Kotlin coroutines cheat sheet',
    platform: 'linkedin',
    scheduledFor: '2026-06-14T10:00:00Z',
    status: 'scheduled',
  },
  {
    id: 'sch-2',
    title: 'Testing matters more than coverage',
    platform: 'twitter',
    scheduledFor: '2026-06-14T14:00:00Z',
    status: 'scheduled',
  },
  {
    id: 'sch-3',
    title: 'Docker multi-stage builds',
    platform: 'bluesky',
    scheduledFor: '2026-06-16T09:00:00Z',
    status: 'queued',
  },
  {
    id: 'sch-4',
    title: 'GraphQL vs REST in 2026',
    platform: 'linkedin',
    scheduledFor: '2026-06-17T10:00:00Z',
    status: 'queued',
  },
  {
    id: 'sch-5',
    title: 'Swiss design in software architecture',
    platform: 'threads',
    scheduledFor: '2026-06-18T12:00:00Z',
    status: 'queued',
  },
]

// ---------------------------------------------------------------------------
// Best Posting Times — heatmap grid data
// Mon-Sun x 0-23 hours, score 0-100
// ---------------------------------------------------------------------------

export const postingTimeSlots: PostingTimeSlot[] = (() => {
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
  const slots: PostingTimeSlot[] = []

  // Seeded pseudo-random for deterministic mock data
  let seed = 42
  function next(): number {
    seed = (seed * 16807 + 0) % 2147483647
    return (seed % 100) / 100
  }

  for (const day of days) {
    for (let hour = 0; hour < 24; hour++) {
      // Base score from seeded random
      const base = next()

      // Boost for business hours (8-17) on weekdays
      const isWeekday = day !== 'Sat' && day !== 'Sun'
      const isBusinessHour = hour >= 8 && hour <= 17
      const boost = isWeekday && isBusinessHour ? 0.3 : 0

      // Peak at 9-11 AM and 14-16 PM on weekdays
      const isPeak = isWeekday && ((hour >= 9 && hour <= 11) || (hour >= 14 && hour <= 16))
      const peakBoost = isPeak ? 0.2 : 0

      const score = Math.min(100, Math.round((base + boost + peakBoost) * 100))
      slots.push({ day, hour, score })
    }
  }

  return slots
})()
