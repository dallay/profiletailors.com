import { ref, onUnmounted, getCurrentInstance, type Ref } from 'vue'
import type { PublicationInvalidation } from '@modules/publishing/infrastructure/publishing.store'

export type PublicationEventReconnectOptions = {
  subscribe: (onInvalidation: (event: PublicationInvalidation) => void) => Promise<null>
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

function jitter(maxDelayMs: number): number {
  return Math.random() * maxDelayMs
}

/**
 * Returns a retry delay in milliseconds for a nonnegative, zero-based attempt index.
 * Base delays are 1, 2, 5, 10, then 30 seconds, plus 0–499 ms of jitter.
 */
function computeNextDelayMs(retryCount: number): number {
  const clampedCount = Math.min(retryCount, BACKOFF_DELAYS_MS.length - 1)
  const baseDelay = BACKOFF_DELAYS_MS[clampedCount] as number
  return Math.floor(baseDelay + jitter(500))
}

/**
 * Creates explicit start/stop controls for a publication subscription in the browser.
 * Connection attempts require authentication, a workspace, and a visible document.
 * A resolved subscription schedules another attempt if disconnected; retry state resets
 * before each attempt, so repeated disconnects currently use the first delay (1–1.499 s).
 * Subscribe should settle when the stream ends and handle its own errors: rejections
 * are not caught or retried here.
 *
 * Hiding cancels pending retries without unsubscribing. Visibility callbacks remain
 * registered after stop; stop unsubscribes and also runs on component unmount.
 */
export function usePublicationEventReconnect(
  options: PublicationEventReconnectOptions,
): PublicationEventReconnect {
  const { subscribe, unsubscribe, isConnected, isAuthenticated, workspaceId, onVisible, onHidden } =
    options

  const isReconnecting = ref(false)

  let stopped = true
  let retryCount = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let visible = true
  let pendingImmediateConnect = false

  const clearTimer = (): void => {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const shouldRun = (): boolean =>
    !stopped && isAuthenticated.value && !!workspaceId.value && visible

  const handleVisibilityVisible = (): void => {
    visible = true
    onVisible()
    if (!isConnected.value && !stopped) {
      if (pendingImmediateConnect) {
        pendingImmediateConnect = false
        clearTimer()
        void attemptConnect()
      }
    }
  }

  const handleVisibilityHidden = (): void => {
    visible = false
    onHidden()
    clearTimer()
  }

  const attemptConnect = async (): Promise<void> => {
    if (!shouldRun()) return

    retryCount = 0

    await subscribe(() => {
      retryCount = 0
      isReconnecting.value = false
    })

    if (!isConnected.value && shouldRun()) {
      scheduleReconnect()
    }
  }

  const scheduleReconnect = (): void => {
    clearTimer()
    if (!shouldRun()) return

    isReconnecting.value = true
    const delayMs = computeNextDelayMs(retryCount)
    retryCount++

    reconnectTimer = setTimeout(() => {
      if (!shouldRun()) return
      void attemptConnect()
    }, delayMs)
  }

  /**
   * Enables connection attempts immediately when visible, or on the next visible transition.
   */
  const start = (): void => {
    stopped = false
    pendingImmediateConnect = true
    visible = document.visibilityState === 'visible'
    if (!visible) {
      onHidden()
    } else {
      onVisible()
      void attemptConnect()
    }
  }

  /**
   * Cancels pending retries and unsubscribes; a later start can enable connections again.
   */
  const stop = (): void => {
    stopped = true
    pendingImmediateConnect = false
    clearTimer()
    isReconnecting.value = false
    unsubscribe()
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      handleVisibilityVisible()
    } else {
      handleVisibilityHidden()
    }
  })

  if (getCurrentInstance()) {
    onUnmounted(stop)
  }

  return { isReconnecting, start, stop }
}
