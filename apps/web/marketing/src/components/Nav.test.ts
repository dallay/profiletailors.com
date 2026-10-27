import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(resolve(process.cwd(), 'src/components/Nav.astro'), 'utf8')

describe('Nav language switch accessibility', () => {
  it('includes the visible language code in the accessible name', () => {
    expect(source).toContain(`aria-label={\`${'${'}langLabel} ${'${'}langSwitch}\`}`)
  })
})
