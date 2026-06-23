import type { Ref } from 'vue'

/**
 * Accessible focus trap for modals, dialogs, and popovers.
 *
 * - Saves the previously focused element on activate
 * - Traps Tab/Shift+Tab within `containerRef`
 * - Returns focus to the trigger element on deactivate
 * - Calls `onClose` when Escape is pressed
 *
 * @example
 * ```ts
 * const modalRef = ref<HTMLElement | null>(null)
 * const { activate, deactivate } = useFocusTrap(modalRef, () => emit('close'))
 * watch(() => props.isOpen, (open) => open ? activate() : deactivate())
 * ```
 */
export function useFocusTrap(containerRef: Ref<HTMLElement | null>, onClose: () => void) {
  let previousActiveElement: HTMLElement | null = null
  let keyHandler: ((e: KeyboardEvent) => void) | null = null

  function getFocusableElements(): HTMLElement[] {
    const container = containerRef.value
    if (!container) return []
    const selectors = [
      'a[href]',
      'button:not([disabled])',
      'textarea:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
      '[contenteditable]',
    ]
    return Array.from(container.querySelectorAll<HTMLElement>(selectors.join(', '))).filter(
      (el) => {
        // Ensure element is visible
        const style = getComputedStyle(el)
        return style.display !== 'none' && style.visibility !== 'hidden'
      },
    )
  }

  function handleKeyDown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault()
      onClose()
      return
    }

    if (e.key !== 'Tab') return

    const focusable = getFocusableElements()
    if (focusable.length === 0) {
      e.preventDefault()
      return
    }

    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (!first || !last) return

    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }

  function activate() {
    if (!containerRef.value) return

    previousActiveElement = document.activeElement as HTMLElement | null

    const focusable = getFocusableElements()
    const firstFocusable = focusable[0]
    if (firstFocusable) {
      firstFocusable.focus()
    } else {
      // If no focusable elements, focus the container itself
      containerRef.value?.focus()
    }

    keyHandler = handleKeyDown
    document.addEventListener('keydown', keyHandler)
  }

  function deactivate() {
    if (keyHandler) {
      document.removeEventListener('keydown', keyHandler)
      keyHandler = null
    }

    // Restore focus to the trigger element
    if (previousActiveElement && document.contains(previousActiveElement)) {
      previousActiveElement.focus()
    }
    previousActiveElement = null
  }

  return { activate, deactivate }
}
