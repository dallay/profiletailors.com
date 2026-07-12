import { describe, expect, it } from 'vitest'

import modulesUrl from '@modules/.gitkeep?url'
import sharedUrl from '@shared/.gitkeep?url'
import layoutsUrl from '@layouts/.gitkeep?url'

describe('path aliases', () => {
  it('resolves modularization foundation aliases', () => {
    expect(modulesUrl).toContain('modules')
    expect(sharedUrl).toContain('shared')
    expect(layoutsUrl).toContain('layouts')
  })
})
