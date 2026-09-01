import { describe, it, expect } from 'vitest'
import { GET } from '../pages/design.md.ts'

describe('design.md API route', () => {
  it('returns 200 OK with text/markdown Content-Type', async () => {
    const res = (await GET({} as never)) as Response
    expect(res.status).toBe(200)
    expect(res.headers.get('Content-Type')).toBe('text/markdown; charset=utf-8')
    expect(res.headers.get('Cache-Control')).toBe('public, max-age=3600')
  })

  it('serves valid markdown body content containing Profile Tailors design spec', async () => {
    const res = (await GET({} as never)) as Response
    const body = await res.text()
    expect(body).toContain('name: Profile Tailors')
    expect(body).toContain('colors:')
    expect(body).toContain('typography:')
  })
})
