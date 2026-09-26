import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import LinkedInCallbackView from './LinkedInCallbackView.vue'
import {
  usePublishingStore,
  type SocialConnectionResult,
} from '@modules/publishing/infrastructure/publishing.store'

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
const routeParams = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))
const replace = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value, params: routeParams.value }),
  useRouter: () => ({ replace }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
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
    routeParams.value = { provider: 'linkedin' }
    replace.mockReset()
    Object.defineProperty(window, 'location', {
      value: { origin: 'http://app.test' },
      configurable: true,
    })
  })

  it('completes callback with code, state, and computed redirect URI before navigating to channel settings', async () => {
    routeQuery.value = { code: 'code-1', state: 'state-raw-value' }
    const publishing = usePublishingStore()
    const complete = vi
      .spyOn(publishing, 'completeProviderConnectionFromCallback')
      .mockResolvedValue(mockConnectionResult)

    mountCallback()
    await flushPromises()

    expect(complete).toHaveBeenCalledWith({
      provider: 'linkedin',
      code: 'code-1',
      state: 'state-raw-value',
      redirectUri: 'http://app.test/integrations/linkedin/callback',
    })
    expect(replace).toHaveBeenCalledWith({
      path: '/settings',
      query: { connected: 'linkedin', panel: 'channels', provider: 'linkedin' },
    })
  })

  it('completes a Threads callback with the unchanged state and provider-specific navigation', async () => {
    routeParams.value = { provider: 'threads' }
    routeQuery.value = { code: 'threads-code', state: 'threads-state' }
    const publishing = usePublishingStore()
    const complete = vi
      .spyOn(publishing, 'completeProviderConnectionFromCallback')
      .mockResolvedValue({ ...mockConnectionResult, provider: 'threads' })

    mountCallback()
    await flushPromises()

    expect(complete).toHaveBeenCalledWith({
      provider: 'threads',
      code: 'threads-code',
      state: 'threads-state',
      redirectUri: 'http://app.test/integrations/threads/callback',
    })
    expect(replace).toHaveBeenCalledWith({
      path: '/settings',
      query: { connected: 'threads', panel: 'channels', provider: 'threads' },
    })
  })

  it('uses provider-neutral denial feedback for a Threads callback', async () => {
    routeParams.value = { provider: 'threads' }
    routeQuery.value = { error: 'access_denied' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeProviderConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('providerCallback.deniedMessage')
  })

  it('uses provider-neutral missing-parameter feedback for a Threads callback', async () => {
    routeParams.value = { provider: 'threads' }
    routeQuery.value = { code: 'threads-code' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeProviderConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('providerCallback.missingParamsMessage')
  })

  it('uses provider-neutral failure feedback for a Threads callback', async () => {
    routeParams.value = { provider: 'threads' }
    routeQuery.value = { code: 'threads-code', state: 'threads-state' }
    const publishing = usePublishingStore()
    vi.spyOn(publishing, 'completeProviderConnectionFromCallback').mockRejectedValue(
      new Error('connection failed'),
    )

    const wrapper = mountCallback()
    await flushPromises()

    expect(wrapper.text()).toContain('providerCallback.failedMessage')
  })

  it('uses provider-neutral retry feedback when a Threads retry fails', async () => {
    routeParams.value = { provider: 'threads' }
    routeQuery.value = { error: 'access_denied' }
    const publishing = usePublishingStore()
    vi.spyOn(publishing, 'connectProviderPersonalProfile').mockRejectedValue(
      new Error('retry failed'),
    )

    const wrapper = mountCallback()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('providerCallback.retryFailedMessage')
  })

  it('does not call completion when LinkedIn returns an OAuth error', async () => {
    routeQuery.value = { error: 'access_denied', error_description: 'Denied by user' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeProviderConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('providerCallback.deniedMessage')
  })

  it('does not call completion when code or state is missing', async () => {
    routeQuery.value = { code: 'code-1' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeProviderConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('providerCallback.missingParamsMessage')
  })

  it('offers retry that starts a new LinkedIn connection', async () => {
    routeQuery.value = { error: 'access_denied' }
    const publishing = usePublishingStore()
    const connect = vi.spyOn(publishing, 'connectProviderPersonalProfile').mockResolvedValue({
      authorizationUrl: 'https://linkedin.example/auth',
      state: 'state-1',
      expiresAt: '2026-06-12T12:10:00Z',
    })

    const wrapper = mountCallback()
    await flushPromises()
    await wrapper.find('button').trigger('click')

    expect(connect).toHaveBeenCalledWith(
      'linkedin',
      'http://app.test/integrations/linkedin/callback',
    )
  })

  it('rejects an unsupported callback provider without calling a completion endpoint', async () => {
    routeParams.value = { provider: 'mastodon' }
    routeQuery.value = { code: 'code-1', state: 'state-1' }
    const publishing = usePublishingStore()
    const complete = vi.spyOn(publishing, 'completeProviderConnectionFromCallback')

    const wrapper = mountCallback()
    await flushPromises()

    expect(complete).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('providerCallback.unsupportedProviderMessage')
  })

  it('keeps the LinkedIn callback alias when the route has no provider parameter', async () => {
    routeParams.value = {}
    routeQuery.value = { code: 'code-1', state: 'state-1' }
    const publishing = usePublishingStore()
    const complete = vi
      .spyOn(publishing, 'completeProviderConnectionFromCallback')
      .mockResolvedValue(mockConnectionResult)

    mountCallback()
    await flushPromises()

    expect(complete).toHaveBeenCalledWith({
      provider: 'linkedin',
      code: 'code-1',
      state: 'state-1',
      redirectUri: 'http://app.test/integrations/linkedin/callback',
    })
  })
})
