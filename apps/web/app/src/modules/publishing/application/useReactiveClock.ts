import { ref, onUnmounted, getCurrentInstance, type Ref } from 'vue'

export type ReactiveClockOptions = {
  onVisible?: () => void
  onHidden?: () => void
}

export type ReactiveClock = {
  now: Ref<Date>
  stop: () => void
}

const MINUTE = 60_000

/**
 * Starts a reactive browser clock aligned to the next wall-clock minute.
 * Updates pause while hidden and refresh immediately when the document becomes visible.
 * Optional callbacks run on visibility changes, not on initial setup or each tick.
 * The returned stop permanently removes timers and the listener; component unmount
 * also stops the clock when called in setup. Callback errors are not caught.
 */
export function useReactiveClock(options: ReactiveClockOptions = {}): ReactiveClock {
  const now = ref(new Date())
  let alignmentTimer: ReturnType<typeof setTimeout> | null = null
  let ticker: ReturnType<typeof setInterval> | null = null
  let stopped = false

  const clearTimers = (): void => {
    if (alignmentTimer) {
      clearInterval(alignmentTimer)
      alignmentTimer = null
    }
    if (ticker) {
      clearInterval(ticker)
      ticker = null
    }
  }

  const start = (): void => {
    if (stopped || document.visibilityState === 'hidden' || ticker) return
    const delay = MINUTE - (Date.now() % MINUTE)
    alignmentTimer = setTimeout(() => {
      alignmentTimer = null
      if (stopped || document.visibilityState === 'hidden') return
      now.value = new Date()
      ticker = setInterval(() => {
        if (document.visibilityState === 'visible') now.value = new Date()
      }, MINUTE)
    }, delay)
  }

  const handleVisibilityChange = (): void => {
    clearTimers()
    if (document.visibilityState === 'hidden') {
      options.onHidden?.()
      return
    }
    if (document.visibilityState === 'visible') {
      now.value = new Date()
      options.onVisible?.()
      start()
    }
  }

  const stop = (): void => {
    if (stopped) return
    stopped = true
    clearTimers()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
  start()
  if (getCurrentInstance()) onUnmounted(stop)

  return { now, stop }
}
