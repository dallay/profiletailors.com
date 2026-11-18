import { describe, expect, it } from 'vitest'
import { OWNER_PRINCIPAL, SUPPORT_PRINCIPAL } from '../e2e/fixtures/test-data'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

describe('admin authentication fixtures', () => {
  it.each([OWNER_PRINCIPAL, SUPPORT_PRINCIPAL])(
    'uses a UUID principal identifier for $email',
    (principal) => {
      expect(principal.principalId).toMatch(UUID_PATTERN)
    },
  )

  it('uses separate principal identifiers for each operator fixture', () => {
    expect(OWNER_PRINCIPAL.principalId).not.toBe(SUPPORT_PRINCIPAL.principalId)
  })
})
