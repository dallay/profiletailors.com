import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import OfflineView from './OfflineView.vue'

const authState = vi.hoisted(() => ({
  sessionChecked: false,
  bootstrapState: 'unreachable',
  isAuthenticated: false,
  hydrateSession: vi.fn(),
  clearLocalSession: vi.fn(),
}))
const retry = vi.hoisted(() => vi.fn())

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => authState,
}))
vi.mock('@/pwa/useOnlineStatus', () => ({
  useOnlineStatus: () => ({ status: { value: 'offline' }, retry }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
}))

describe('OfflineView', () => {
  beforeEach(() => {
    authState.sessionChecked = false
    authState.bootstrapState = 'unreachable'
    authState.isAuthenticated = false
    authState.hydrateSession.mockReset().mockResolvedValue(undefined)
    retry.mockReset().mockResolvedValue(undefined)
  })

  it.each([
    { sessionChecked: false, bootstrapState: 'unauthenticated' },
    { sessionChecked: true, bootstrapState: 'unreachable' },
  ])('hydrates the session after retry when recovery is needed', async (state) => {
    authState.sessionChecked = state.sessionChecked
    authState.bootstrapState = state.bootstrapState

    const wrapper = mount(OfflineView)
    await wrapper.get('button').trigger('click')

    expect(retry).toHaveBeenCalledOnce()
    expect(authState.hydrateSession).toHaveBeenCalledOnce()
  })
})
