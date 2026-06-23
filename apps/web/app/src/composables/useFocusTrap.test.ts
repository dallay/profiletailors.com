import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useFocusTrap } from './useFocusTrap'

// ---------------------------------------------------------------------------
// Test harness — a tiny component that mounts the composable.
// ---------------------------------------------------------------------------

function createHarness() {
  const closeHandler = vi.fn()

  const Harness = defineComponent({
    setup() {
      const containerRef = ref<HTMLElement | null>(null)
      const { activate, deactivate } = useFocusTrap(containerRef, closeHandler)

      return { containerRef, activate, deactivate, closeHandler }
    },
    render() {
      return h('div', { ref: 'containerRef', tabindex: -1 }, [
        h('button', { 'data-testid': 'first' }, 'First'),
        h('button', { 'data-testid': 'second' }, 'Second'),
        h('button', { 'data-testid': 'third' }, 'Third'),
      ])
    },
  })

  const wrapper = mount(Harness, { attachTo: document.body })
  return { wrapper, closeHandler }
}

/** Collect wrappers so afterEach can unmount them all. */
const wrappers: ReturnType<typeof mount>[] = []

describe('useFocusTrap', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    wrappers.forEach((w) => w.unmount())
    wrappers.length = 0
    document.body.innerHTML = ''
  })

  it('focuses the first focusable element on activate', () => {
    const { wrapper } = createHarness()
    wrappers.push(wrapper)

    // Place focus on the document body first
    document.body.focus()
    expect(document.activeElement).toBe(document.body)

    wrapper.vm.activate()
    const buttons = wrapper.findAll('button')
    expect(buttons[0]).toBeDefined()
    expect(document.activeElement).toBe(buttons[0]!.element)
  })

  it('focuses the container itself when no focusable children exist', () => {
    const closeHandler = vi.fn()

    const Empty = defineComponent({
      setup() {
        const containerRef = ref<HTMLElement | null>(null)
        const { activate } = useFocusTrap(containerRef, closeHandler)
        return { containerRef, activate }
      },
      render() {
        // A div with no focusable children
        return h('div', { ref: 'containerRef', tabindex: -1 }, ['Just text'])
      },
    })

    const wrapper = mount(Empty, { attachTo: document.body })
    wrappers.push(wrapper)

    wrapper.vm.activate()
    // The container itself should receive focus (tabindex="-1" allows this)
    const containerEl = wrapper.find('div').element
    expect(document.activeElement).toBe(containerEl)
  })

  it('calls onClose when Escape is pressed', () => {
    const { wrapper, closeHandler } = createHarness()
    wrappers.push(wrapper)

    wrapper.vm.activate()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(closeHandler).toHaveBeenCalledTimes(1)
  })

  it('traps Tab cycling forward', () => {
    const { wrapper } = createHarness()
    wrappers.push(wrapper)

    wrapper.vm.activate()
    const buttons = wrapper.findAll('button')

    // Focus is on first button after activate
    expect(buttons[0]).toBeDefined()
    expect(document.activeElement).toBe(buttons[0]!.element)

    // Press Tab on the last button should wrap to first
    expect(buttons[2]).toBeDefined()
    ;(buttons[2]!.element as HTMLElement).focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }))
    expect(buttons[0]).toBeDefined()
    expect(document.activeElement).toBe(buttons[0]!.element)
  })

  it('traps Shift+Tab cycling backward', () => {
    const { wrapper } = createHarness()
    wrappers.push(wrapper)

    wrapper.vm.activate()
    const buttons = wrapper.findAll('button')

    // Focus is on first button after activate. Press Shift+Tab should wrap to last.
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true }))
    const lastButton = buttons[buttons.length - 1]
    expect(lastButton).toBeDefined()
    expect(document.activeElement).toBe(lastButton!.element)
  })

  it('prevents default Tab when there are no focusable elements', () => {
    const closeHandler = vi.fn()

    const Empty = defineComponent({
      setup() {
        const containerRef = ref<HTMLElement | null>(null)
        const { activate } = useFocusTrap(containerRef, closeHandler)
        return { containerRef, activate }
      },
      render() {
        return h('div', { ref: 'containerRef', tabindex: -1 })
      },
    })

    const wrapper = mount(Empty, { attachTo: document.body })
    wrappers.push(wrapper)

    wrapper.vm.activate()

    const event = new KeyboardEvent('keydown', { key: 'Tab' })
    const preventSpy = vi.spyOn(event, 'preventDefault')
    document.dispatchEvent(event)

    expect(preventSpy).toHaveBeenCalled()
  })

  it('saves and restores the previously focused element', () => {
    // Create an external trigger button
    const trigger = document.createElement('button')
    trigger.setAttribute('data-testid', 'trigger')
    document.body.appendChild(trigger)
    trigger.focus()
    expect(document.activeElement).toBe(trigger)

    const { wrapper } = createHarness()
    wrappers.push(wrapper)

    wrapper.vm.activate()
    // Focus moves inside the trap
    const buttons = wrapper.findAll('button')
    expect(buttons[0]).toBeDefined()
    expect(document.activeElement).toBe(buttons[0]!.element)

    wrapper.vm.deactivate()
    // Focus should return to trigger
    expect(document.activeElement).toBe(trigger)

    document.body.removeChild(trigger)
  })

  it('does not restore focus when previous element is removed from DOM', () => {
    const { wrapper } = createHarness()
    wrappers.push(wrapper)

    // Set a removed element as previous active
    const removedEl = document.createElement('button')
    document.body.appendChild(removedEl)
    removedEl.focus()
    document.body.removeChild(removedEl)

    wrapper.vm.activate()
    wrapper.vm.deactivate()

    // Should not throw — focus stays on the element focused within the trap
    expect(() => document.activeElement).not.toThrow()
  })

  it('deactivate removes the keydown listener', () => {
    const { wrapper, closeHandler } = createHarness()
    wrappers.push(wrapper)

    wrapper.vm.activate()
    wrapper.vm.deactivate()

    // Dispatch Escape after deactivate — should NOT call closeHandler
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(closeHandler).not.toHaveBeenCalled()
  })

  it('handles non-Tab and non-Escape keys without side effects', () => {
    const { wrapper, closeHandler } = createHarness()
    wrappers.push(wrapper)

    wrapper.vm.activate()

    // Arrow keys should not trigger any behavior
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    expect(closeHandler).not.toHaveBeenCalled()
  })

  it('does not crash when activate is called before mount', () => {
    const closeHandler = vi.fn()
    const containerRef = ref<HTMLElement | null>(null)
    const { activate } = useFocusTrap(containerRef, closeHandler)

    // Activate with null container — should not throw
    expect(() => activate()).not.toThrow()

    // The handler should still not fire since we deactivated
    expect(closeHandler).not.toHaveBeenCalled()
  })
})
