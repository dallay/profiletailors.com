import { onUnmounted, getCurrentInstance } from 'vue'
import type { CalendarRange } from './calendarRange'

export type CalendarRevalidationOptions = {
  fetchCalendar: (range: CalendarRange) => Promise<void>
  debounceMs?: number
  maxAgeMs?: number
  now?: () => number
  setTimeoutFn?: (callback: () => void, delayMs: number) => ReturnType<typeof setTimeout>
  clearTimeoutFn?: (handle: ReturnType<typeof setTimeout>) => void
}

export type CalendarRevalidation = {
  request: (range: CalendarRange) => void
  setVisible: (visible: boolean) => void
  stop: () => void
}

const DEFAULT_DEBOUNCE_MS = 150
const DEFAULT_MAX_AGE_MS = 60_000

/**
 * Coalesces requests into a fetch of the latest range while visible, without overlapping
 * fetches. The delay starts with the first pending request and defaults to 150 ms.
 * Visibility restoration fetches dirty data or data at least maxAgeMs old (default 60,000 ms).
 * The optional now clock must return milliseconds on a consistent time scale.
 *
 * Fetch rejections are not caught; fetchCalendar must handle errors to avoid an unhandled
 * rejection from the timer. Stop cancels pending work but does not abort an active fetch.
 * When called in component setup, stop runs on unmount.
 */
export function useCalendarRevalidation(
  options: CalendarRevalidationOptions,
): CalendarRevalidation {
  const debounceMs = options.debounceMs ?? DEFAULT_DEBOUNCE_MS
  const maxAgeMs = options.maxAgeMs ?? DEFAULT_MAX_AGE_MS
  const setTimer = options.setTimeoutFn ?? ((callback, delay) => setTimeout(callback, delay))
  const clearTimerHandle = options.clearTimeoutFn ?? ((handle) => clearTimeout(handle))
  const now = options.now ?? Date.now
  let visible = true
  let stopped = false
  let dirty = false
  let latestRange: CalendarRange | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastFetchedAt = 0
  let inFlight = false

  const clearTimer = (): void => {
    if (timer) {
      clearTimerHandle(timer)
      timer = null
    }
  }

  const execute = async (): Promise<void> => {
    timer = null
    if (stopped || !visible || !latestRange || !dirty) return
    if (inFlight) {
      schedule()
      return
    }
    const range = latestRange
    dirty = false
    inFlight = true
    try {
      await options.fetchCalendar(range)
      lastFetchedAt = now()
    } finally {
      inFlight = false
    }
  }

  const schedule = (): void => {
    if (stopped || !visible || !latestRange || timer) return
    timer = setTimer(() => {
      void execute()
    }, debounceMs)
  }

  /**
   * Marks the latest range dirty and schedules a fetch when visible.
   */
  const request = (range: CalendarRange): void => {
    latestRange = range
    dirty = true
    schedule()
  }

  /**
   * Pauses pending work while hidden and schedules dirty or stale data when visible.
   */
  const setVisible = (nextVisible: boolean): void => {
    visible = nextVisible
    if (!visible) {
      clearTimer()
      return
    }
    if (!dirty && now() - lastFetchedAt < maxAgeMs) return
    dirty = true
    schedule()
  }

  /**
   * Permanently stops scheduling and clears the pending timer without aborting a fetch.
   */
  const stop = (): void => {
    if (stopped) return
    stopped = true
    clearTimer()
  }

  if (getCurrentInstance()) onUnmounted(stop)

  return { request, setVisible, stop }
}
