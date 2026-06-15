import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'

export interface UsePopoverDismissalOptions {
  /** Ref pointing to the popover container element. */
  container: Ref<HTMLElement | null>
  /** Optional ref pointing to the trigger element. When provided, focus is restored to it on Escape/click-outside. */
  trigger?: Ref<HTMLElement | null>
}

export interface UsePopoverDismissalApi {
  open: Ref<boolean>
  /** Flip the open state. */
  toggle: () => void
  /** Force close. Does not call focus restore. */
  close: () => void
  /** Force open. Does not call focus restore. */
  openIt: () => void
}

/**
 * Shared open-state + dismissal logic for hand-rolled popovers.
 *
 * Behavior:
 * - `document.click` closes when the target is outside `container.value` AND
 *   (when `trigger` is provided) outside `trigger.value` — a click on the trigger
 *   is a toggle, not a dismiss.
 * - `document.keydown` with `Escape` closes and restores focus to `trigger.value`
 *   after `nextTick()` so the popover's removal from the DOM does not suppress
 *   the focus request. If the trigger is no longer in the document, focus is
 *   NOT restored.
 * - `useRoute().path` watcher closes the popover but does NOT call `.focus()` —
 *   the browser shifts focus to the new route.
 * - `onBeforeUnmount` removes the `document` listeners and stops the route
 *   watcher. No store deps; unit-testable without Pinia.
 */
export function usePopoverDismissal(opts: UsePopoverDismissalOptions): UsePopoverDismissalApi {
  const { container, trigger } = opts
  const open = ref(false)
  const route = useRoute()

  function close() {
    open.value = false
  }

  function openIt() {
    open.value = true
  }

  function toggle() {
    open.value = !open.value
  }

  function handleDocumentClick(event: MouseEvent) {
    if (!open.value) return
    const target = event.target
    if (!(target instanceof Node)) return

    const insideContainer = container.value?.contains(target) ?? false
    const insideTrigger = trigger ? (trigger.value?.contains(target) ?? false) : false
    if (insideContainer || insideTrigger) return

    close()
    if (trigger) {
      // Defer focus so the popover's removal from the DOM doesn't suppress the call.
      void nextTick().then(() => {
        const t = trigger.value
        if (t && document.contains(t)) {
          t.focus()
        }
      })
    }
  }

  function handleKeydown(event: KeyboardEvent) {
    if (event.key !== 'Escape') return
    if (!open.value) return
    event.stopPropagation()
    close()
    if (trigger) {
      void nextTick().then(() => {
        const t = trigger.value
        if (t && document.contains(t)) {
          t.focus()
        }
      })
    }
  }

  // Route-change close — NO focus restore. The browser shifts focus on navigation.
  const stopRouteWatch = watch(
    () => route.path,
    () => {
      if (open.value) close()
    },
  )

  onMounted(() => {
    document.addEventListener('click', handleDocumentClick)
    document.addEventListener('keydown', handleKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleDocumentClick)
    document.removeEventListener('keydown', handleKeydown)
    stopRouteWatch()
  })

  return { open, toggle, close, openIt }
}
