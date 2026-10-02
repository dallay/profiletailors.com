import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  mount: vi.fn(),
  hydrateSession: vi.fn(),
  reportStartupFailure: vi.fn(),
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    createApp: vi.fn(() => ({ use: vi.fn(), mount: mocks.mount })),
  }
})
vi.mock('./App.vue', () => ({ default: { name: 'TestAdminApp' } }))
vi.mock('./router', () => ({ default: {} }))
vi.mock('./i18n', () => ({ messages: {} }))
vi.mock('./stores/auth.store', () => ({
  useAdminAuthStore: () => ({ hydrateSession: mocks.hydrateSession }),
}))
vi.mock('@/lib/sentry', () => ({
  configureAdminSentry: () => mocks.reportStartupFailure,
}))

describe('admin bootstrap', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    mocks.hydrateSession.mockResolvedValue(undefined)
    mocks.mount.mockReturnValue({})
  })

  it('mounts the app after hydrating the session', async () => {
    await import('./main')

    expect(mocks.hydrateSession).toHaveBeenCalled()
    expect(mocks.mount).toHaveBeenCalledWith('#app')
    expect(mocks.reportStartupFailure).not.toHaveBeenCalled()
  })

  it('reports hydration failures to the error reporter', async () => {
    const failure = new Error('session unreachable')
    mocks.hydrateSession.mockRejectedValueOnce(failure)

    await import('./main')

    expect(mocks.reportStartupFailure).toHaveBeenCalledWith(failure)
  })
})
