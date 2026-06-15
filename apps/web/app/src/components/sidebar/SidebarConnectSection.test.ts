import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarConnectSection, { type ConnectChannel } from './SidebarConnectSection.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const providers: ConnectChannel[] = [
  { id: 'linkedin', label: 'LinkedIn profile', badge: 'in' },
  { id: 'threads', label: 'Threads', badge: '@' },
  { id: 'bluesky', label: 'Bluesky', badge: 'b' },
  { id: 'facebook', label: 'Facebook', badge: 'f' },
]

describe('SidebarConnectSection', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders 4 connect buttons + a More button', () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })
    const buttons = wrapper.findAll('button')
    // 4 providers + 1 More button = 5
    expect(buttons.length).toBe(5)
  })

  it('emits connect(linkedin) when the LinkedIn row is clicked', async () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    // Find the LinkedIn button — it contains "LinkedIn profile" and "+ connect"
    const li = wrapper.findAll('button').find((b) => b.text().includes('LinkedIn profile'))
    expect(li).toBeTruthy()
    await li?.trigger('click')

    expect(wrapper.emitted('connect')).toBeTruthy()
    const payload = wrapper.emitted('connect')?.[0]?.[0] as ConnectChannel
    expect(payload.id).toBe('linkedin')
  })

  it('shows a transient "coming soon" message on Threads click and auto-clears after 3500ms', async () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    const threads = wrapper.findAll('button').find((b) => b.text().includes('Threads'))
    await threads?.trigger('click')

    // The connect message paragraph should appear (aria-live)
    const live = wrapper.find('[aria-live="polite"]')
    expect(live.exists()).toBe(true)
    expect(live.text()).toContain('Threads')

    // Advance past the 3500ms default
    vi.advanceTimersByTime(3500)
    await wrapper.vm.$nextTick()

    // After timer fires, the message paragraph is gone
    expect(wrapper.find('[aria-live="polite"]').exists()).toBe(false)
  })

  it('emits more() on the More button', async () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    const more = wrapper.findAll('button').find((b) => b.text().includes('channels.more'))
    expect(more).toBeTruthy()
    await more?.trigger('click')

    expect(wrapper.emitted('more')).toBeTruthy()
  })

  it('cancels the pending timer on unmount', async () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    const threads = wrapper.findAll('button').find((b) => b.text().includes('Threads'))
    await threads?.trigger('click')

    // Message present
    expect(wrapper.find('[aria-live="polite"]').exists()).toBe(true)

    // Unmount before 3500ms — no error, no late callback
    wrapper.unmount()
    vi.advanceTimersByTime(5000)
  })
})
