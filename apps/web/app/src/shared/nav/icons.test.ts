import { describe, expect, it } from 'vitest'
import { LayoutGrid, Link } from '@lucide/vue'
import { navIconByName, resolveNavIcon } from './icons'

describe('navigation icons', () => {
  it('resolves registered icon names', () => {
    expect(resolveNavIcon('CalendarDays')).toBe(navIconByName.CalendarDays)
  })

  it('resolves the shortlinks icon', () => {
    expect(resolveNavIcon('Link')).toBe(Link)
  })

  it('falls back to the default icon for unknown names', () => {
    expect(resolveNavIcon('UnknownIcon')).toBe(LayoutGrid)
  })
})
