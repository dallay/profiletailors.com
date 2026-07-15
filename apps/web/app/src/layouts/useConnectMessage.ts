import { onBeforeUnmount, ref, type Ref } from 'vue'

export interface UseConnectMessageOptions {
  defaultDurationMs?: number
}

export interface UseConnectMessageApi {
  message: Ref<string>
  show: (text: string, durationMs?: number) => void
  clear: () => void
}

/**
 * Owns a transient message ref plus the `setTimeout` cleanup that auto-clears it.
 * Used by the sidebar's "connect" subpanel for the "coming soon" / "connecting…"
 * status line. The pending timer id is held in a local ref so `show` (to cancel a
 * prior pending) and `onBeforeUnmount` (to cancel on unmount) can clear it.
 *
 * `clear()` is a no-op when no timer is pending. Default `defaultDurationMs` is
 * `3500` (matches the spec and the prior inline `setTimeout` value).
 */
export function useConnectMessage(opts: UseConnectMessageOptions = {}): UseConnectMessageApi {
  const { defaultDurationMs = 3500 } = opts
  const message = ref('')
  const timer = ref<ReturnType<typeof setTimeout> | null>(null)

  function clear() {
    if (timer.value !== null) {
      clearTimeout(timer.value)
      timer.value = null
    }
    message.value = ''
  }

  function show(text: string, durationMs: number = defaultDurationMs) {
    // Cancel any pending auto-clear so it does not run mid-show
    if (timer.value !== null) {
      clearTimeout(timer.value)
    }
    message.value = text
    timer.value = setTimeout(() => {
      message.value = ''
      timer.value = null
    }, durationMs)
  }

  onBeforeUnmount(() => {
    if (timer.value !== null) {
      clearTimeout(timer.value)
      timer.value = null
    }
  })

  return { message, show, clear }
}
