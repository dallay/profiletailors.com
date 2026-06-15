import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, ref, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { usePopoverDismissal } from './usePopoverDismissal'

// ---------------------------------------------------------------------------
// A shared, reactive route object so each test can swap the path AFTER mount.
// ---------------------------------------------------------------------------

// The route object MUST be stable across renders so that `() => route.path`
// re-reads the same property. We use `reactive` (not `ref`) and read `.path`
// off the same proxy every time, which lets the watcher re-track.
import { reactive } from 'vue'

interface RouteLike {
  path: string
}
const routeState = reactive<RouteLike>({ path: '/' })
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

// ---------------------------------------------------------------------------
// Test harness — a tiny SFC that mounts the composable and exposes its refs.
// ---------------------------------------------------------------------------

interface HarnessExposed {
  containerRef: ReturnType<typeof ref<HTMLElement | null>>
  triggerRef: ReturnType<typeof ref<HTMLElement | null>>
  open: () => boolean
  toggle: () => void
  close: () => void
  openIt: () => void
}

function mountHarness(opts: { withTrigger?: boolean } = {}): {
  wrapper: ReturnType<typeof mount>
  exposed: HarnessExposed
} {
  const containerRef = ref<HTMLElement | null>(null)
  const triggerRef = ref<HTMLElement | null>(null)
  let api!: ReturnType<typeof usePopoverDismissal>

  const Harness = defineComponent({
    setup() {
      const triggerMaybe = opts.withTrigger ? triggerRef : undefined
      api = usePopoverDismissal({ container: containerRef, trigger: triggerMaybe })
      return { containerRef, triggerRef }
    },
    render() {
      return h('div', [
        opts.withTrigger
          ? h(
              'button',
              {
                ref: (el: Element | null) => {
                  if (el instanceof HTMLElement) triggerRef.value = el
                },
                class: 'trigger',
                onClick: (e: MouseEvent) => {
                  e.stopPropagation()
                  api.toggle()
                },
              },
              'Trigger',
            )
          : null,
        h(
          'div',
          {
            ref: (el: unknown) => {
              if (el instanceof HTMLElement) containerRef.value = el
            },
            class: 'container',
            'data-open': api.open.value ? 'true' : 'false',
          },
          api.open.value ? 'OPEN' : 'CLOSED',
        ),
      ])
    },
  })

  const wrapper = mount(Harness, { attachTo: document.body })

  const exposed: HarnessExposed = {
    containerRef,
    triggerRef,
    open: () => api.open.value,
    toggle: () => api.toggle(),
    close: () => api.close(),
    openIt: () => api.openIt(),
  }

  return { wrapper, exposed }
}

beforeEach(() => {
  routeState.path = '/'
  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
})

afterEach(() => {
  vi.restoreAllMocks()
})

// ---------------------------------------------------------------------------
// Tests — covering the 6 scenarios in design §6
// ---------------------------------------------------------------------------

describe('usePopoverDismissal', () => {
  it('click on trigger toggles open', async () => {
    const { wrapper, exposed } = mountHarness({ withTrigger: true })
    expect(exposed.open()).toBe(false)

    await wrapper.find('button.trigger').trigger('click')
    expect(exposed.open()).toBe(true)

    await wrapper.find('button.trigger').trigger('click')
    expect(exposed.open()).toBe(false)
  })

  it('Escape closes the popover and restores focus to the trigger', async () => {
    const { exposed } = mountHarness({ withTrigger: true })
    exposed.openIt()
    await nextTick()
    expect(exposed.open()).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    await nextTick()

    expect(exposed.open()).toBe(false)
    expect(document.activeElement).toBe(exposed.triggerRef.value)
  })

  it('click outside the container closes the popover and restores focus', async () => {
    const { exposed } = mountHarness({ withTrigger: true })
    exposed.openIt()
    await nextTick()
    expect(exposed.open()).toBe(true)

    const outside = document.createElement('div')
    document.body.appendChild(outside)
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    await nextTick()

    expect(exposed.open()).toBe(false)
    expect(document.activeElement).toBe(exposed.triggerRef.value)
    document.body.removeChild(outside)
  })

  it('click on the trigger does NOT restore focus (toggle is the source)', async () => {
    const { wrapper, exposed } = mountHarness({ withTrigger: true })
    exposed.openIt()
    await nextTick()
    expect(exposed.open()).toBe(true)

    document.body.tabIndex = -1
    document.body.focus()

    await wrapper.find('button.trigger').trigger('click')
    await nextTick()
    await nextTick()

    expect(exposed.open()).toBe(false)
    expect(document.activeElement).not.toBe(exposed.triggerRef.value)
  })

  it('route change closes the popover and does NOT restore focus', async () => {
    const { exposed } = mountHarness({ withTrigger: true })
    exposed.openIt()
    await nextTick()
    expect(exposed.open()).toBe(true)

    // Move focus off the trigger so we can detect if it's stolen
    document.body.tabIndex = -1
    document.body.focus()
    const before = document.activeElement

    // Swap the route's path — composable's watcher must close
    routeState.path = '/scheduler'
    await nextTick()
    await nextTick()

    expect(exposed.open()).toBe(false)
    // The trigger must NOT have been re-focused by the route-change handler
    expect(document.activeElement).toBe(before)
  })

  it('listeners are torn down on unmount', async () => {
    const { wrapper, exposed } = mountHarness({ withTrigger: true })
    exposed.openIt()
    await nextTick()

    const removeSpy = vi.spyOn(document, 'removeEventListener')
    wrapper.unmount()
    expect(removeSpy).toHaveBeenCalledWith('click', expect.any(Function))
    expect(removeSpy).toHaveBeenCalledWith('keydown', expect.any(Function))

    // Dispatch events after unmount — no errors, no state mutation
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    document.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
  })

  it('does not steal focus when trigger is detached from the document', async () => {
    const { exposed } = mountHarness({ withTrigger: true })
    exposed.openIt()
    await nextTick()

    const trigger = exposed.triggerRef.value
    expect(trigger).toBeTruthy()
    trigger?.parentElement?.removeChild(trigger)
    expect(document.contains(trigger!)).toBe(false)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    await nextTick()

    expect(exposed.open()).toBe(false)
    expect(document.activeElement).not.toBe(trigger)
  })
})
