import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AccountClosureSection from './AccountClosureSection.vue'

const closeAccountMock = vi.hoisted(() => vi.fn())
const logoutMock = vi.hoisted(() => vi.fn())
const replaceMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: replaceMock }),
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      locale: { value: 'en' },
    },
  }),
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  closeAccount: (...args: unknown[]) => closeAccountMock(...args),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    accessToken: 'test-token',
    logout: logoutMock,
  }),
}))

function mountComponent() {
  return mount(AccountClosureSection, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
    },
  })
}

describe('AccountClosureSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the account closure card with title and description', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('[data-testid="settings-account-closure-panel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.accountClosure.title')
    expect(wrapper.text()).toContain('settings.accountClosure.description')
  })

  it('shows the confirmation input and close button', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('#closure-confirmation-input').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.accountClosure.closeButton')
  })

  it('disables the close button when confirmation is not DELETE', () => {
    const wrapper = mountComponent()
    const button = wrapper.find('[data-testid="closure-submit-button"]')

    // disabled attribute is '' when present
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('enables the close button when confirmation is DELETE', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('#closure-confirmation-input')
    await input.setValue('DELETE')

    const button = wrapper.find('[data-testid="closure-submit-button"]')
    // disabled attribute is undefined when not disabled
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('calls closeAccount and logout on successful submission', async () => {
    closeAccountMock.mockResolvedValue(undefined)
    logoutMock.mockResolvedValue(undefined)

    const wrapper = mountComponent()
    const input = wrapper.find('#closure-confirmation-input')
    await input.setValue('DELETE')

    const button = wrapper.find('[data-testid="closure-submit-button"]')
    await button.trigger('click')
    await flushPromises()

    expect(closeAccountMock).toHaveBeenCalledWith('test-token')
    expect(logoutMock).toHaveBeenCalledOnce()
    expect(replaceMock).toHaveBeenCalledWith('/login')
  })

  it('shows rateLimited error on 429 response', async () => {
    closeAccountMock.mockRejectedValue({ status: 429 })

    const wrapper = mountComponent()
    const input = wrapper.find('#closure-confirmation-input')
    await input.setValue('DELETE')

    const button = wrapper.find('[data-testid="closure-submit-button"]')
    await button.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="closure-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.accountClosure.rateLimited')
    expect(logoutMock).not.toHaveBeenCalled()
  })

  it('shows error detail on non-429 failure', async () => {
    closeAccountMock.mockRejectedValue({ status: 400, detail: 'Bad request' })

    const wrapper = mountComponent()
    const input = wrapper.find('#closure-confirmation-input')
    await input.setValue('DELETE')

    const button = wrapper.find('[data-testid="closure-submit-button"]')
    await button.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="closure-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Bad request')
    expect(logoutMock).not.toHaveBeenCalled()
  })

  it('shows generic error when API error has no detail', async () => {
    closeAccountMock.mockRejectedValue({ status: 500 })

    const wrapper = mountComponent()
    const input = wrapper.find('#closure-confirmation-input')
    await input.setValue('DELETE')

    const button = wrapper.find('[data-testid="closure-submit-button"]')
    await button.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="closure-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.accountClosure.error')
  })
})
