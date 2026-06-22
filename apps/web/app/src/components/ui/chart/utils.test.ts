import { describe, it, expect, vi, beforeEach } from 'vitest'
import { componentToString } from './utils'
import type { ChartConfig } from '.'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

// isClient — pretend we're in a browser so componentToString doesn't bail early
vi.mock('@vueuse/core', () => ({
  isClient: true,
}))

// reka-ui — stub useId and createContext so chart/index.ts loads cleanly
vi.mock('reka-ui', () => ({
  useId: vi.fn(() => 'test-id'),
  createContext: vi.fn(() => [vi.fn(), vi.fn()]),
}))

// ---------------------------------------------------------------------------
// Minimal chart fixtures
// ---------------------------------------------------------------------------

const config: ChartConfig = {}

// A minimal Vue-compatible component stub
const StubComponent = {
  props: ['payload', 'config', 'x'],
  template: '<div class="stub">{{ JSON.stringify(payload) }}</div>',
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('componentToString', () => {
  beforeEach(() => {
    // Clear the module-level cache between tests by creating a fresh document
    document.body.innerHTML = ''
  })

  it('returns a function when called in a browser context', () => {
    const renderer = componentToString(config, StubComponent as any)
    expect(typeof renderer).toBe('function')
  })

  it('renders HTML for the given data payload', () => {
    const renderer = componentToString(config, StubComponent as any)!
    const html = renderer({ value: 42 }, 0)
    expect(typeof html).toBe('string')
    expect(html).toBeTruthy()
  })

  it('returns the same HTML string for identical data (cache hit)', () => {
    const renderer = componentToString(config, StubComponent as any)!
    const data = { score: 10 }
    const first = renderer(data, 0)
    const second = renderer(data, 0)
    expect(first).toBe(second)
  })

  it('returns different HTML for different data payloads (cache miss)', () => {
    const renderer = componentToString(config, StubComponent as any)!
    const html1 = renderer({ score: 10 }, 0)
    const html2 = renderer({ score: 99 }, 0)
    expect(html1).not.toBe(html2)
  })

  it('produces consistent cache keys regardless of object key insertion order', () => {
    // The localeCompare sort in serializeKey means { b:2, a:1 } and { a:1, b:2 }
    // serialize to the same key and therefore hit the same cache entry.
    const renderer = componentToString(config, StubComponent as any)!

    const dataAB = { a: 1, b: 2 }
    const dataBA = { b: 2, a: 1 }

    const htmlAB = renderer(dataAB, 0)
    const htmlBA = renderer(dataBA, 0)

    // Both objects have the same contents — they should be cache-equivalent
    expect(htmlAB).toBe(htmlBA)
  })

  it('handles data wrapped in a { data: ... } envelope (unovis crosshair format)', () => {
    const renderer = componentToString(config, StubComponent as any)!
    // unovis wraps data points as { data: actual }
    const wrapped = renderer({ data: { views: 100 } }, 5)
    const direct = renderer({ views: 100 }, 5)
    // Both should render (not throw)
    expect(typeof wrapped).toBe('string')
    expect(typeof direct).toBe('string')
  })

  it('returns the same result for keys with locale-sensitive ordering', () => {
    // Keys that differ only by locale-sensitive comparison but same ASCII letters
    // The important property: { zebra: 1, apple: 2 } and { apple: 2, zebra: 1 }
    // produce the same serialized key after localeCompare sort.
    const renderer = componentToString(config, StubComponent as any)!

    const data1 = { zebra: 1, apple: 2, mango: 3 }
    const data2 = { mango: 3, apple: 2, zebra: 1 }

    const html1 = renderer(data1, 0)
    const html2 = renderer(data2, 0)

    expect(html1).toBe(html2)
  })
})
