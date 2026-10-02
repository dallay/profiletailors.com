import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  mount: vi.fn(),
  hydrateSession: vi.fn(),
  initPwa: vi.fn(),
  reportStartupFailure: vi.fn(),
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    createApp: vi.fn(() => ({ use: vi.fn(), mount: mocks.mount })),
  }
})
vi.mock('./App.vue', () => ({ default: { name: 'TestApp' } }))
vi.mock('./router', () => ({ default: {} }))
vi.mock('@shared/i18n', () => ({ default: {} }))
vi.mock('@/pwa/registerPwa', () => ({ initPwa: mocks.initPwa }))
vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ hydrateSession: mocks.hydrateSession }),
}))
vi.mock('@modules/settings/infrastructure/settings.store', () => ({
  useSettingsStore: vi.fn(),
}))
vi.mock('@shared/lib/sentry', () => ({
  configureAppSentry: () => mocks.reportStartupFailure,
}))

describe('app bootstrap', () => {
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
    expect(mocks.initPwa).toHaveBeenCalled()
    expect(mocks.mount).toHaveBeenCalledWith('#app')
    expect(mocks.reportStartupFailure).not.toHaveBeenCalled()
  })

  it('reports hydration failures without blocking startup', async () => {
    const failure = new Error('session unreachable')
    mocks.hydrateSession.mockRejectedValueOnce(failure)

    await import('./main')

    expect(mocks.reportStartupFailure).toHaveBeenCalledWith(failure)
    expect(mocks.mount).toHaveBeenCalledWith('#app')
  })

  it('reports mount failures to the error reporter', async () => {
    const failure = new Error('mount boom')
    mocks.mount.mockImplementationOnce(() => {
      throw failure
    })

    await import('./main')

    expect(mocks.reportStartupFailure).toHaveBeenCalledWith(failure)
  })
})
