import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, reactive, ref, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { usePopoverDismissal } from './usePopoverDismissal'

// ---------------------------------------------------------------------------
// A shared, reactive route object so each test can swap the path AFTER mount.
// ---------------------------------------------------------------------------

interface RouteLike {
  path: string
  fullPath: string
}
const routeState = reactive<RouteLike>({ path: '/', fullPath: '/' })
const getRouteFullPath = () => routeState.fullPath

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

/** Collect wrappers here so afterEach can unmount them all. */
const wrappers: ReturnType<typeof mount>[] = []

function mountHarness(opts: { withTrigger?: boolean; withRoute?: boolean } = {}): {
  wrapper: ReturnType<typeof mount>
  exposed: HarnessExposed
} {
  const containerRef = ref<HTMLElement | null>(null)
  const triggerRef = ref<HTMLElement | null>(null)
  let api!: ReturnType<typeof usePopoverDismissal>

  const Harness = defineComponent({
    setup() {
      const triggerMaybe = opts.withTrigger ? triggerRef : undefined
      const getRoute = opts.withRoute !== false ? getRouteFullPath : undefined
      api = usePopoverDismissal({ container: containerRef, trigger: triggerMaybe, getRouteFullPath: getRoute })
      return { containerRef, triggerRef }
    },
    render() {
      return h('div', [
        opts.withTrigger
          ? h(
              'button',
              {
                ref: (el) => {
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
  wrappers.push(wrapper)

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
  routeState.fullPath = '/'
  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
})

afterEach(() => {
  vi.restoreAllMocks()
  for (const w of wrappers.splice(0)) w.unmount()
})

// ---------------------------------------------------------------------------
// Tests
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

    document.body.tabIndex = -1
    document.body.focus()
    const before = document.activeElement

    routeState.path = '/scheduler'
    routeState.fullPath = '/scheduler'
    await nextTick()
    await nextTick()

    expect(exposed.open()).toBe(false)
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

  it('works without a route accessor (no route watcher installed)', async () => {
    const { exposed } = mountHarness({ withTrigger: true, withRoute: false })
    exposed.openIt()
    await nextTick()
    expect(exposed.open()).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    await nextTick()
    expect(exposed.open()).toBe(false)
  })
})

