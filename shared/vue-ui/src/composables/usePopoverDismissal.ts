import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

export interface UsePopoverDismissalOptions {
  container: Ref<HTMLElement | null>
  trigger?: Ref<HTMLElement | null>
  getRouteFullPath?: () => string
}

export interface UsePopoverDismissalApi {
  open: Ref<boolean>
  toggle: () => void
  close: () => void
  openIt: () => void
}

export function usePopoverDismissal(opts: UsePopoverDismissalOptions): UsePopoverDismissalApi {
  const { container, trigger, getRouteFullPath } = opts
  const open = ref(false)

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
      nextTick().then(() => {
        const t = trigger.value
        if (t && document.contains(t)) {
          t.focus()
        }
      }).catch(() => undefined)
    }
  }

  function handleKeydown(event: KeyboardEvent) {
    if (event.key !== 'Escape') return
    if (!open.value) return
    event.stopPropagation()
    close()
    if (trigger) {
      nextTick().then(() => {
        const t = trigger.value
        if (t && document.contains(t)) {
          t.focus()
        }
      }).catch(() => undefined)
    }
  }

  let stopRouteWatch: (() => void) | undefined
  if (getRouteFullPath) {
    stopRouteWatch = watch(
      () => getRouteFullPath(),
      () => {
        if (open.value) close()
      },
    )
  }

  onMounted(() => {
    document.addEventListener('click', handleDocumentClick)
    document.addEventListener('keydown', handleKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleDocumentClick)
    document.removeEventListener('keydown', handleKeydown)
    stopRouteWatch?.()
  })

  return { open, toggle, close, openIt }
}
