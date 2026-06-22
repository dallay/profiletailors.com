import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarMenuSkeleton from './SidebarMenuSkeleton.vue'

// ---------------------------------------------------------------------------
// Stub crypto.getRandomValues so the test is deterministic
// ---------------------------------------------------------------------------
const _originalGetRandomValues = globalThis.crypto?.getRandomValues.bind(globalThis.crypto)

function stubGetRandomValues(returnValue: Uint32Array) {
  vi.stubGlobal('crypto', {
    ...globalThis.crypto,
    getRandomValues: vi.fn((arr: Uint32Array) => {
      if (arr instanceof Uint32Array && arr.length > 0) {
        arr[0] = returnValue[0]
      }
      return arr
    }),
  } as Crypto)
}

function restoreCrypto() {
  vi.stubGlobal('crypto', {
    ...globalThis.crypto,
    getRandomValues: _originalGetRandomValues,
  } as Crypto)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('SidebarMenuSkeleton', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    restoreCrypto()
  })

  it('renders without crashing when showIcon is false', () => {
    stubGetRandomValues(new Uint32Array([0]))
    const wrapper = mount(SidebarMenuSkeleton, { props: { showIcon: false } })
    expect(wrapper.find('[data-sidebar="menu-skeleton"]').exists()).toBe(true)
  })

  it('renders with showIcon when provided', () => {
    stubGetRandomValues(new Uint32Array([0]))
    const wrapper = mount(SidebarMenuSkeleton, { props: { showIcon: true } })
    expect(wrapper.find('[data-sidebar="menu-skeleton-icon"]').exists()).toBe(true)
  })

  it('generates a skeleton width as a percentage between 50% and 90%', () => {
    stubGetRandomValues(new Uint32Array([0]))
    const wrapper = mount(SidebarMenuSkeleton, { props: { showIcon: false } })

    const skeletonEl = wrapper.find('[data-sidebar="menu-skeleton-text"]')
    expect(skeletonEl.exists()).toBe(true)

    const style = skeletonEl.attributes('style') ?? ''
    const widthMatch = style.match(/--skeleton-width:\s*([^;]+)/)
    expect(widthMatch).toBeTruthy()

    const pct = parseFloat(widthMatch![1])
    expect(pct).toBeGreaterThanOrEqual(50)
    expect(pct).toBeLessThan(90)
  })

  it('uses crypto.getRandomValues instead of Math.random', () => {
    const getRandomValuesSpy = vi.fn((arr: Uint32Array) => {
      arr[0] = 0
      return arr
    })
    vi.stubGlobal('crypto', {
      ...globalThis.crypto,
      getRandomValues: getRandomValuesSpy,
    } as Crypto)

    mount(SidebarMenuSkeleton, { props: { showIcon: false } })

    expect(getRandomValuesSpy).toHaveBeenCalled()
    const calledWith = getRandomValuesSpy.mock.calls[0]?.[0] as Uint32Array
    expect(calledWith).toBeInstanceOf(Uint32Array)
    expect(calledWith.length).toBe(1)
  })

  it('generates varying widths when given different random values', () => {
    stubGetRandomValues(new Uint32Array([0xffffffff])) // max uint32 → ~90%
    const wrapperMax = mount(SidebarMenuSkeleton, { props: { showIcon: false } })
    const maxStyle =
      wrapperMax.find('[data-sidebar="menu-skeleton-text"]').attributes('style') ?? ''
    const maxMatch = maxStyle.match(/--skeleton-width:\s*([^;]+)/)
    const maxPct = parseFloat(maxMatch![1])

    stubGetRandomValues(new Uint32Array([0])) // 0 → 50%
    const wrapperMin = mount(SidebarMenuSkeleton, { props: { showIcon: false } })
    const minStyle =
      wrapperMin.find('[data-sidebar="menu-skeleton-text"]').attributes('style') ?? ''
    const minMatch = minStyle.match(/--skeleton-width:\s*([^;]+)/)
    const minPct = parseFloat(minMatch![1])

    // Both are in the valid range; max should be strictly < 90%
    expect(maxPct).toBeGreaterThanOrEqual(50)
    expect(maxPct).toBeLessThan(90)
    expect(minPct).toBeGreaterThanOrEqual(50)
    expect(minPct).toBeLessThan(90)
    // Max differs from min (different random values)
    expect(maxPct).not.toBe(minPct)
  })

  it('passes through the class prop', () => {
    stubGetRandomValues(new Uint32Array([0]))
    const wrapper = mount(SidebarMenuSkeleton, {
      props: { showIcon: false, class: 'custom-skeleton-class' },
    })

    const root = wrapper.find('[data-sidebar="menu-skeleton"]')
    expect(root.classes()).toContain('custom-skeleton-class')
  })
})
