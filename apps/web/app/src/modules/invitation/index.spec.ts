import { describe, expect, it } from 'vitest'
import { AcceptInvitationView } from './index'

describe('invitation barrel', () => {
  it('re-exports AcceptInvitationView', () => {
    expect(AcceptInvitationView).toBeDefined()
  })
})
