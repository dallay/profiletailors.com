import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ConfigurationView from './ConfigurationView.vue'

const mockRequest = vi.fn()
const mockHasPermission = vi.fn(() => true)
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest, hasPermission: mockHasPermission }),
}))

const messages = {
  en: {
    configuration: {
      title: 'Registration Mode',
      currentMode: 'Current mode',
      changeTo: 'Change mode',
      changeConfirm: 'Change the registration mode to {mode}? This takes effect immediately.',
      changeSuccess: 'Registration mode updated.',
    },
    common: { loading: 'Loading...', error: 'An error occurred.' },
  },
}

function createView() {
  setActivePinia(createPinia())
  const i18n = createI18n({ legacy: false, locale: 'en', messages })
  return mount(ConfigurationView, {
    global: {
      plugins: [i18n],
    },
  })
}

describe('ConfigurationView', () => {
  beforeEach(() => {
    mockRequest.mockReset()
    mockHasPermission.mockReset()
    mockHasPermission.mockReturnValue(true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows a loading state before the current mode resolves', () => {
    mockRequest.mockReturnValue(new Promise(() => {}))
    const wrapper = createView()
    expect(wrapper.text()).toContain('Loading...')
  })

  it('shows the current mode in a read-only view when the caller lacks manage permission', async () => {
    mockHasPermission.mockReturnValue(false)
    mockRequest.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ mode: 'OPEN' }) })
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.text()).toContain('OPEN')
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.text() === 'Change mode')).toBe(false)
  })

  it('renders a write control gated on manage permission', async () => {
    mockHasPermission.mockReturnValue(true)
    mockRequest.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ mode: 'OPEN' }) })
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text() === 'Change mode')).toBe(true)
  })

  it('shows an error state when the initial fetch fails', async () => {
    mockRequest.mockResolvedValueOnce({ ok: false, status: 403 })
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('An error occurred.')
  })

  it('confirms before submitting a mode change and sends a random Idempotency-Key', async () => {
    const confirmSpy = vi.fn(() => true)
    vi.stubGlobal('confirm', confirmSpy)
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'generated-key') })
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ mode: 'OPEN' }) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ mode: 'CLOSED' }) })
    const wrapper = createView()
    await flushPromises()

    await wrapper.find('select').setValue('CLOSED')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Change mode')
      ?.trigger('click')
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalledWith(
      'Change the registration mode to CLOSED? This takes effect immediately.',
    )
    const changeCall = mockRequest.mock.calls[1]
    expect(changeCall?.[0]).toBe('/api/admin/configuration/registration-mode')
    expect(changeCall?.[1]?.method).toBe('POST')
    expect(changeCall?.[1]?.headers?.['Idempotency-Key']).toBe('generated-key')
    expect(wrapper.text()).toContain('Registration mode updated.')
    expect(wrapper.text()).toContain('CLOSED')
  })

  it('does not submit the change when the operator cancels the confirmation', async () => {
    vi.stubGlobal(
      'confirm',
      vi.fn(() => false),
    )
    mockRequest.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ mode: 'OPEN' }) })
    const wrapper = createView()
    await flushPromises()

    await wrapper.find('select').setValue('CLOSED')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Change mode')
      ?.trigger('click')
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledTimes(1)
  })

  it('shows an error state when the change request fails', async () => {
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    )
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'generated-key') })
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ mode: 'OPEN' }) })
      .mockResolvedValueOnce({ ok: false, status: 400 })
    const wrapper = createView()
    await flushPromises()

    await wrapper.find('select').setValue('CLOSED')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Change mode')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('An error occurred.')
  })
})
