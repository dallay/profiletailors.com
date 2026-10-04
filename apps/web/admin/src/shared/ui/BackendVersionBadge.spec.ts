import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import BackendVersionBadge from './BackendVersionBadge.vue'

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest }),
}))

const messages = {
  en: {
    system: {
      backendBuild: 'Backend build',
      backendRevision: 'Revision',
      backendBuiltAt: 'Built',
      backendUnavailable: 'Build information unavailable',
    },
    common: { loading: 'Loading...' },
  },
}

function createBadge() {
  return mount(BackendVersionBadge, {
    global: { plugins: [createI18n({ legacy: false, locale: 'en', messages })] },
  })
}

describe('BackendVersionBadge', () => {
  beforeEach(() => mockRequest.mockReset())
  afterEach(() => vi.restoreAllMocks())

  it('renders backend version, short revision, and localized build time', async () => {
    mockRequest.mockResolvedValue({
      ok: true,
      status: 200,
      json: () =>
        Promise.resolve({
          service: 'smp',
          version: '1.2.3',
          revision: '1234567890abcdef',
          builtAt: '2026-09-30T12:00:00.000Z',
        }),
    })
    const wrapper = createBadge()
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledWith('/api/admin/system/build-info')
    expect(wrapper.get('[data-testid="backend-version-badge"]').text()).toContain('API')
    expect(wrapper.text()).toContain('v1.2.3')
    expect(wrapper.text()).toContain('1234567')
    expect(wrapper.get('[data-testid="backend-version-badge"]').attributes('title')).toContain(
      '1234567890abcdef',
    )
  })

  it('announces loading and unavailable states', async () => {
    let resolveRequest!: (response: {
      ok: boolean
      status: number
      json: () => Promise<never>
    }) => void
    mockRequest.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve
      }),
    )
    const wrapper = createBadge()
    expect(wrapper.get('output').text()).toBe('Loading...')

    resolveRequest({ ok: false, status: 503, json: () => Promise.reject(new Error('offline')) })
    await flushPromises()
    expect(wrapper.get('output').text()).toBe('Build information unavailable')
  })
})
