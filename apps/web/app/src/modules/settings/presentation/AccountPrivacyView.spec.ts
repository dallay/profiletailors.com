import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AccountPrivacyView from './AccountPrivacyView.vue'
import { usePrivacyStore } from '@modules/settings/infrastructure/privacy.store'

const closeAccountMock = vi.hoisted(() => vi.fn())
const logoutMock = vi.hoisted(() => vi.fn())
const pushMock = vi.hoisted(() => vi.fn())
const fetchRequestsMock = vi.hoisted(() => vi.fn())
const submitRequestMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ accessToken: 'valid-token', logout: logoutMock }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  closeAccount: closeAccountMock,
}))

vi.mock('@modules/settings/infrastructure/privacy.store', () => ({
  usePrivacyStore: () => ({
    requests: [],
    loading: false,
    fetchRequests: fetchRequestsMock,
    submitRequest: submitRequestMock,
  }),
}))

describe('AccountPrivacyView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    closeAccountMock.mockResolvedValue(undefined)
    logoutMock.mockResolvedValue(undefined)
    fetchRequestsMock.mockResolvedValue(undefined)
    submitRequestMock.mockResolvedValue({} as never)
  })

  it('renders header', () => {
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    expect(wrapper.find('h1').text()).toBeTruthy()
  })

  it('renders DSAR form and request list components', () => {
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    expect(wrapper.findComponent({ name: 'DsarRequestForm' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'DsarRequestList' }).exists()).toBe(true)
  })

  it('passes requests and loading to DsarRequestList', () => {
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    const list = wrapper.findComponent({ name: 'DsarRequestList' })
    expect(list.props('requests')).toEqual([])
    expect(list.props('loading')).toBe(false)
  })

  it('opens delete modal when delete button is clicked', async () => {
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    const deleteBtn = wrapper.findAll('button').find(b => b.text().includes('delete'))
    await deleteBtn?.trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  })

  it('does not submit when confirmation does not match DELETE', async () => {
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    await wrapper.findAll('button').find(b => b.text().includes('delete'))?.trigger('click')
    const input = wrapper.find('input[id="delete-confirm-input"]')
    await input.setValue('WRONG')
    await wrapper.find('form').trigger('submit')
    expect(closeAccountMock).not.toHaveBeenCalled()
  })

  it('calls closeAccount and redirects to /login on success', async () => {
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    await wrapper.findAll('button').find(b => b.text().includes('delete'))?.trigger('click')
    const input = wrapper.find('input[id="delete-confirm-input"]')
    await input.setValue('DELETE')
    await wrapper.find('form').trigger('submit')
    expect(closeAccountMock).toHaveBeenCalledWith('valid-token')
    await wrapper.vm.$nextTick()
    expect(logoutMock).toHaveBeenCalled()
    expect(pushMock).toHaveBeenCalledWith('/login')
  })

  it('shows rate limit error when API returns 429', async () => {
    closeAccountMock.mockRejectedValue({ status: 429 })
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    await wrapper.findAll('button').find(b => b.text().includes('delete'))?.trigger('click')
    await wrapper.find('input[id="delete-confirm-input"]').setValue('DELETE')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('rateLimited')
  })

  it('shows generic error when API fails with non-429', async () => {
    closeAccountMock.mockRejectedValue({ status: 500, detail: 'Server error' })
    const wrapper = mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    await wrapper.findAll('button').find(b => b.text().includes('delete'))?.trigger('click')
    await wrapper.find('input[id="delete-confirm-input"]').setValue('DELETE')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Server error')
  })

  it('calls privacy.fetchRequests on mount', () => {
    mount(AccountPrivacyView, { global: { stubs: { teleport: true } } })
    expect(fetchRequestsMock).toHaveBeenCalled()
  })
})
