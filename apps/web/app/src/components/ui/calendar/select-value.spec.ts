import { describe, expect, it } from 'vitest'
import { selectNumberValue } from './select-value'

describe('selectNumberValue', () => {
  it('reads value from select target', () => {
    const target = document.createElement('select')
    const option = document.createElement('option')
    option.value = '7'
    target.appendChild(option)
    target.value = '7'
    expect(selectNumberValue({ target } as unknown as Event)).toBe(7)
  })

  it('returns NaN-safe fallback for non-select targets', () => {
    expect(
      selectNumberValue({ target: document.createElement('div') } as unknown as Event),
    ).toBeNaN()
  })
})
