// @vitest-environment node

import { experimental_AstroContainer as AstroContainer } from 'astro/container'
import { describe, expect, it } from 'vitest'
import VersionBadge from './VersionBadge.astro'

describe('VersionBadge', () => {
  it('renders the version and short SHA with the build time', async () => {
    const container = await AstroContainer.create()
    const result = await container.renderToString(VersionBadge)

    expect(result).toContain('v0.2.13 (c3d4e5f)')
    expect(result).toContain('title="2026-09-18T16:00:00.000Z"')
  })
})
