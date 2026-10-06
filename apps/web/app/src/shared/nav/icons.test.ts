import { describe, expect, it } from 'vitest'
import { LayoutGrid } from '@lucide/vue'
import { navIconByName, resolveNavIcon } from './icons'

describe('navigation icons', () => {
  it('resolves registered icon names', () => {
    expect(resolveNavIcon('CalendarDays')).toBe(navIconByName.CalendarDays)
  })

  it('falls back to the default icon for unknown names', () => {
    expect(resolveNavIcon('UnknownIcon')).toBe(LayoutGrid)
  })
})
