import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlannedAreaView from '@/views/PlannedAreaView.vue'
import { messages } from '@/i18n'

vi.mock('vue-router', () => ({
  useRoute: () => ({ name: 'overview', params: {}, query: {} }),
}))

function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    fallbackLocale: 'en',
    messages,
  })
  return mount(PlannedAreaView, {
    global: {
      plugins: [i18n],
    },
    attachTo: document.body,
  })
}

describe('PlannedAreaView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the area label with an explicit planned message', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="planned-area-message"]').text()).toContain(
      'planned and not yet available',
    )
    expect(wrapper.text()).toContain('Overview')
    wrapper.unmount()
  })

  it('issues zero API requests while rendering', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountView()
    await flushPromises()
    expect(fetchMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
