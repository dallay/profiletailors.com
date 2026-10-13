import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ShortlinkAnalyticsView from './ShortlinkAnalyticsView.vue'

const { listWorkspaceShortlinkMetrics } = vi.hoisted(() => ({
  listWorkspaceShortlinkMetrics: vi.fn(),
}))

vi.mock('@modules/shortlinks', () => ({
  listWorkspaceShortlinkMetrics,
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

const metric = (id: string, recordedRedirects: number) => ({
  id,
  shortCode: `code-${id}`,
  shortUrl: `https://pt.test/${id}`,
  destinationUrl: `https://destination.test/${id}`,
  status: 'ACTIVE',
  createdAt: '2026-10-08T12:00:00Z',
  expiresAt: null,
  version: 1,
  recordedRedirects,
})

describe('ShortlinkAnalyticsView', () => {
  it('reloads the first page when the active workspace changes', async () => {
    const workspace = await import('@modules/workspace')
    const { createPinia, setActivePinia } = await import('pinia')
    setActivePinia(createPinia())
    const store = workspace.useWorkspaceStore()
    store.setActiveWorkspaceId('workspace-a')
    listWorkspaceShortlinkMetrics.mockResolvedValue({ links: [], nextCursor: null })
    const wrapper = mount(ShortlinkAnalyticsView)
    await flushPromises()
    store.setActiveWorkspaceId('workspace-b')
    await flushPromises()
    expect(listWorkspaceShortlinkMetrics.mock.calls.map(([cursor]) => cursor)).toEqual([null, null])
    wrapper.unmount()
  })

  it('ignores results from a previous workspace request', async () => {
    const { createPinia, setActivePinia } = await import('pinia')
    const workspace = await import('@modules/workspace')
    setActivePinia(createPinia())
    const store = workspace.useWorkspaceStore()
    store.setActiveWorkspaceId('workspace-a')
    let resolveWorkspaceA:
      | ((value: { links: ReturnType<typeof metric>[]; nextCursor: null }) => void)
      | undefined
    listWorkspaceShortlinkMetrics
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveWorkspaceA = resolve
          }),
      )
      .mockResolvedValueOnce({ links: [metric('workspace-b', 4)], nextCursor: null })

    const wrapper = mount(ShortlinkAnalyticsView)
    await flushPromises()
    store.setActiveWorkspaceId('workspace-b')
    await flushPromises()
    resolveWorkspaceA?.({ links: [metric('workspace-a', 2)], nextCursor: null })
    await flushPromises()

    expect(wrapper.text()).toContain('workspace-b')
    expect(wrapper.text()).not.toContain('workspace-a')
    wrapper.unmount()
  })

  it('renders zero and positive stored click counts', async () => {
    listWorkspaceShortlinkMetrics.mockResolvedValue({
      links: [metric('zero', 0), metric('positive', 8)],
      nextCursor: null,
    })

    const wrapper = mount(ShortlinkAnalyticsView)
    await flushPromises()

    expect(wrapper.findAll('tbody tr').map((row) => row.findAll('td')[2].text())).toEqual([
      '0',
      '8',
    ])
    expect(wrapper.text()).toContain('https://pt.test/zero')
    expect(wrapper.text()).toContain('https://pt.test/positive')
  })

  it('requests the next page and returns to the previous cursor', async () => {
    listWorkspaceShortlinkMetrics
      .mockResolvedValueOnce({ links: [metric('first', 1)], nextCursor: 'cursor-2' })
      .mockResolvedValueOnce({ links: [metric('second', 2)], nextCursor: 'cursor-3' })
      .mockResolvedValueOnce({ links: [metric('first', 1)], nextCursor: 'cursor-2' })

    const wrapper = mount(ShortlinkAnalyticsView)
    await flushPromises()
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()

    expect(listWorkspaceShortlinkMetrics.mock.calls).toEqual([[null], ['cursor-2']])
    expect(wrapper.text()).toContain('https://pt.test/second')
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(listWorkspaceShortlinkMetrics.mock.calls).toEqual([[null], ['cursor-2'], [null]])
    expect(wrapper.text()).toContain('https://pt.test/first')
  })

  it('recovers the previous page after a next-page request fails', async () => {
    listWorkspaceShortlinkMetrics
      .mockResolvedValueOnce({ links: [metric('first', 1)], nextCursor: 'cursor-2' })
      .mockResolvedValueOnce({ links: [metric('second', 2)], nextCursor: 'cursor-3' })
      .mockRejectedValueOnce(new Error('request failed'))
      .mockResolvedValueOnce({ links: [metric('first', 1)], nextCursor: 'cursor-2' })

    const wrapper = mount(ShortlinkAnalyticsView)
    await flushPromises()
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('shortlinks.listError')
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(listWorkspaceShortlinkMetrics.mock.calls).toEqual([[null], ['cursor-2'], [null], [null]])
    expect(wrapper.text()).toContain('https://pt.test/first')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows loading while a page request is pending', async () => {
    let resolvePage:
      | ((page: { links: ReturnType<typeof metric>[]; nextCursor: null }) => void)
      | undefined
    listWorkspaceShortlinkMetrics.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePage = resolve
        }),
    )

    const wrapper = mount(ShortlinkAnalyticsView)
    await nextTick()
    expect(wrapper.get('[role="status"]').text()).toBe('common.loading')
    resolvePage?.({ links: [], nextCursor: null })
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('shows the empty state when no links are returned', async () => {
    listWorkspaceShortlinkMetrics.mockResolvedValue({ links: [], nextCursor: null })

    const wrapper = mount(ShortlinkAnalyticsView)
    await flushPromises()

    expect(wrapper.text()).toContain('shortlinks.empty')
  })

  it('shows an alert when loading links fails', async () => {
    let rejectPage: ((reason: Error) => void) | undefined
    listWorkspaceShortlinkMetrics.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectPage = reject
        }),
    )

    const wrapper = mount(ShortlinkAnalyticsView)
    rejectPage?.(new Error('request failed'))
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('shortlinks.listError')
  })
})
