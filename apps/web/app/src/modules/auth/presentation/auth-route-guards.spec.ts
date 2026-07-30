import { describe, expect, it } from 'vitest'
import router from '@/router'

describe('authentication route contracts', () => {
  it('keeps restricted auth routes in place without synthetic unavailable routes', () => {
    expect(router.resolve('/register?email=user@example.com').name).toBe('register')
    expect(router.resolve('/forgot-password').name).toBe('forgot-password')
    expect(router.resolve('/reset-password?token=opaque').name).toBe('reset-password')
    expect(router.hasRoute('registration-unavailable')).toBe(false)
    expect(router.hasRoute('password-recovery-unavailable')).toBe(false)
  })

  it('keeps reset password session-agnostic and standalone', () => {
    const route = router.resolve({ name: 'reset-password', query: { token: 'opaque' } })
    expect(route.meta.guestOnly).not.toBe(true)
    expect(route.meta.standalone).toBe(true)
    expect(route.fullPath).toBe('/reset-password?token=opaque')
  })

  it('uses named standalone routes for all auth and recovery content', () => {
    for (const name of ['login', 'register', 'forgot-password', 'reset-password', 'verify-email']) {
      expect(router.resolve({ name }).meta.standalone).toBe(true)
    }
  })
})
