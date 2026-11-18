import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminAuthStore } from '@/stores/auth.store'

const ADMIN_AUTHORIZATION_PATH = '/oauth2/authorization/pt-admin'

describe('admin BFF login initiation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts the separate pt-admin authorization flow', () => {
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()

    store.signIn('/waitlist')

    expect(assign).toHaveBeenCalledTimes(1)
    const target = assign.mock.calls[0]?.[0] ?? ''
    expect(target).toContain(ADMIN_AUTHORIZATION_PATH)
    expect(target).not.toContain('pt-app')
    expect(target).toContain('returnTo=%2Fwaitlist')
  })

  it('requests the admin root when no return destination is given', () => {
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()

    store.signIn()

    const target = assign.mock.calls[0]?.[0] ?? ''
    expect(target).toContain(ADMIN_AUTHORIZATION_PATH)
    expect(target).toContain('returnTo=%2F')
  })

  it.each([
    ['protocol-relative host', '//attacker.example/admin'],
    ['absolute external url', 'https://attacker.example/steal'],
    ['backslash host', '/\\attacker.example/admin'],
    ['non path scheme', 'javascript:alert(1)'],
    ['relative traversal', '../../admin'],
  ])('replaces a %s return destination with the admin root', (_label, candidate) => {
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()

    store.signIn(candidate)

    const target = assign.mock.calls[0]?.[0] ?? ''
    expect(target).toContain('returnTo=%2F')
    expect(target).not.toContain('attacker')
  })

  it('keeps a same-origin return path with its query and fragment', () => {
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()

    store.signIn('/waitlist?status=pending#top')

    const target = assign.mock.calls[0]?.[0] ?? ''
    expect(target).toContain('returnTo=%2Fwaitlist%3Fstatus%3Dpending%23top')
  })

  it('does not keep a denied admin session when a new login starts', () => {
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()
    store.accessDenied = true

    store.signIn('/users')

    expect(store.accessDenied).toBe(false)
    const target = assign.mock.calls[0]?.[0] ?? ''
    expect(target).toContain(ADMIN_AUTHORIZATION_PATH)
  })
})
