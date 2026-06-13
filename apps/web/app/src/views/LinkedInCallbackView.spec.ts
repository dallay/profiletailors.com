import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import LinkedInCallbackView from './LinkedInCallbackView.vue'
import { usePublishingStore, type SocialConnectionResult } from '@/stores/publishing'

const mockConnectionResult: SocialConnectionResult = {
  connectionId: 'conn-1',
  workspaceId: 'ws-1',
  provider: 'linkedin',
  status: 'connected',
  account: {
    accountId: 'acc-1',
    providerAccountId: 'pa-1',
    displayName: 'Test User',
    kind: 'PROFILE',
    profileUrn: 'urn:li:person:123',
  },
}

const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))
const replace = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value }),
  useRouter: () => ({ replace }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/lib/auth-api', () => ({
  createApiFetch: () =>
    async function apiFetch<T>() {
      return {} as T
    },
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

function mountCallback() {
  return mount(LinkedInCallbackView, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        Loader2: true,
        CheckCircle2: true,
        TriangleAlert: true,
      },
    },
  })
}

describe('LinkedInCallbackView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeQuery.value = {}
    replace.mockReset()
    Object.defineProperty(window, 'location', {
      value: { origin: 'http://app.test' },
      configurable: true,
    })
  })

  it('completes callback with code, state, and computed redirect URI before navigating to settings', async () => {
    routeQuery.value = { code: 'code-1', state: 'state-raw-value' }
    const publishing = usePublishingStore()
    const complete = vi
      .spyOn(publishing, 'completeLinkedInConnectionFromCallback')
      .mockResolvedValue(mockConnectionResult)

    mountCallback()
    await flushPromises()

    expect(complete).toHaveBeenCalledWith({
      code: 'code-1',
      state: 'state-raw-value',
      redirectUri: 'http://app.test/integrations/linkedin/callback',
    })
    expect(replace).toHaveBeenCalledWith({ path: '/settings', query: { connected: 'linkedin' } })
  })

  it('does not call completion when LinkedIn returns an OAuth error', async () => {
    routeQuery.value = { error: 'access_denied', error_description: 'Denied by user' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeLinkedInConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Denied by user')
  })

  it('does not call completion when code or state is missing', async () => {
    routeQuery.value = { code: 'code-1' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeLinkedInConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('linkedinCallback.missingParamsMessage')
  })

  it('offers retry that starts a new LinkedIn connection', async () => {
    routeQuery.value = { error: 'access_denied' }
    const publishing = usePublishingStore()
    const connect = vi.spyOn(publishing, 'connectLinkedInPersonalProfile').mockResolvedValue({
      authorizationUrl: 'https://linkedin.example/auth',
      state: 'state-1',
      expiresAt: '2026-06-12T12:10:00Z',
    })

    const wrapper = mountCallback()
    await flushPromises()
    await wrapper.find('button').trigger('click')

    expect(connect).toHaveBeenCalledWith('http://app.test/integrations/linkedin/callback')
  })
})
