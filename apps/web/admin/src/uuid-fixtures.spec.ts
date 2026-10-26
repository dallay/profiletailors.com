import { describe, expect, it } from 'vitest'
import { OWNER_PRINCIPAL, SUPPORT_PRINCIPAL, mockTokens } from '../e2e/fixtures/test-data'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

describe('admin authentication fixtures', () => {
  it.each([OWNER_PRINCIPAL, SUPPORT_PRINCIPAL])(
    'keeps the token principal aligned with the session principal for $email',
    (principal) => {
      const tokens = mockTokens(principal.email)
      expect(tokens.principalId).toMatch(UUID_PATTERN)
      expect(tokens.principalId).toBe(principal.principalId)
      expect(tokens.accessToken).toBe(`e2e-access-token-for-${principal.email}`)
      expect(tokens.workspaceId).toBeNull()
    },
  )
})
