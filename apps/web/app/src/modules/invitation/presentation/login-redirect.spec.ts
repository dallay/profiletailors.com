import { describe, expect, it } from 'vitest'
import { buildLoginRedirect } from './login-redirect'

describe('buildLoginRedirect', () => {
  it('returns the bare path when there is no query', () => {
    expect(buildLoginRedirect('/invite/abc', {})).toBe('/invite/abc')
  })

  it('appends a serialized query string when present', () => {
    expect(buildLoginRedirect('/invite/abc', { foo: 'bar', n: '1' })).toBe(
      '/invite/abc?foo=bar&n=1',
    )
  })
})
