import { ref, onUnmounted, getCurrentInstance, watch, type Ref } from 'vue'
import type { PublicationInvalidation } from '@modules/publishing/infrastructure/publishing.store'

export type PublicationEventReconnectOptions = {
  subscribe: (
    onInvalidation: (event: PublicationInvalidation) => void,
  ) => Promise<null | 'unavailable'>
  unsubscribe: () => void
  isConnected: Ref<boolean>
  isAuthenticated: Ref<boolean>
  workspaceId: Ref<string | null>
  onVisible: () => void
  onHidden: () => void
}

export type PublicationEventReconnect = {
  isReconnecting: Ref<boolean>
  start: () => void
  stop: () => void
}

const BACKOFF_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000] as const

export function usePublicationEventReconnect(
  options: PublicationEventReconnectOptions,
): PublicationEventReconnect {
  const { subscribe, unsubscribe, isConnected, isAuthenticated, workspaceId, onVisible, onHidden } =
    options
  const isReconnecting = ref(false)
  let stopped = true
  let generation = 0
  let inFlight = false
  let unavailable = false
  let retryCount = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let stopWatching: (() => void) | undefined

  const clearTimer = (): void => {
    clearTimeout(reconnectTimer)
    reconnectTimer = undefined
    isReconnecting.value = false
  }

  const shouldRun = (): boolean =>
    !stopped &&
    !unavailable &&
    isAuthenticated.value &&
    !!workspaceId.value &&
    document.visibilityState === 'visible'

  const scheduleReconnect = (): void => {
    if (!shouldRun() || reconnectTimer !== undefined) return
    isReconnecting.value = true
    const delay = BACKOFF_DELAYS_MS[Math.min(retryCount, BACKOFF_DELAYS_MS.length - 1)] ?? 30_000
    retryCount += 1
    reconnectTimer = setTimeout(
      () => {
        reconnectTimer = undefined
        void attemptConnect()
      },
      Math.floor(delay + Math.random() * 500),
    )
  }

  const attemptConnect = async (): Promise<void> => {
    if (!shouldRun() || inFlight || isConnected.value) return
    const attemptGeneration = generation
    inFlight = true
    isReconnecting.value = false
    const result = await subscribe(() => {
      if (attemptGeneration === generation && !stopped) isReconnecting.value = false
    })
    if (stopped || attemptGeneration !== generation) return
    inFlight = false
    unavailable = result === 'unavailable'
    if (!isConnected.value) scheduleReconnect()
  }

  const handleVisibilityChange = (): void => {
    if (stopped) return
    if (document.visibilityState === 'visible') {
      onVisible()
      if (reconnectTimer === undefined) void attemptConnect()
    } else {
      onHidden()
      clearTimer()
    }
  }

  const start = (): void => {
    if (!stopped) return
    stopped = false
    generation += 1
    unavailable = false
    retryCount = 0
    stopWatching = watch(
      isConnected,
      (connected) => {
        if (connected) {
          retryCount = 0
          clearTimer()
        }
      },
      { flush: 'sync' },
    )
    document.addEventListener('visibilitychange', handleVisibilityChange)
    handleVisibilityChange()
  }

  const stop = (): void => {
    stopped = true
    generation += 1
    inFlight = false
    stopWatching?.()
    stopWatching = undefined
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    clearTimer()
    unsubscribe()
  }

  if (getCurrentInstance()) onUnmounted(stop)
  return { isReconnecting, start, stop }
}
