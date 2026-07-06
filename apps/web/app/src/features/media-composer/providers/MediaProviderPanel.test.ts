import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MediaProviderPanel from './MediaProviderPanel.vue'

vi.mock('@/lib/media-providers', () => ({
  searchProviderPhotos: vi.fn(),
  importProviderPhoto: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

import * as api from '@/lib/media-providers'

const baseProps = () => ({
  workspaceId: 'ws-1',
  token: 't',
})

describe('MediaProviderPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    ;(api.searchProviderPhotos as unknown as ReturnType<typeof vi.fn>).mockReset()
    ;(api.importProviderPhoto as unknown as ReturnType<typeof vi.fn>).mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders a search input and an empty state', () => {
    const wrapper = mount(MediaProviderPanel, { props: baseProps() })
    expect(wrapper.find('[data-testid="media-provider-search"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('composer.mediaPicker.provider.emptyTitle')
  })

  it('does not call the API on mount', () => {
    mount(MediaProviderPanel, { props: baseProps() })
    expect(api.searchProviderPhotos).not.toHaveBeenCalled()
  })

  it('calls searchProviderPhotos when the author submits a query', async () => {
    ;(api.searchProviderPhotos as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [
        {
          externalId: 'unsplash:abc',
          previewUrl: 'https://cdn.example/abc/preview',
          fullUrl: 'https://cdn.example/abc/full',
          width: 100,
          height: 100,
          authorName: 'A',
          authorUrl: 'https://example/a',
          sourceUrl: 'https://unsplash.com/abc',
        },
      ],
      page: { number: 1, size: 20, total: 1 },
    })
    const wrapper = mount(MediaProviderPanel, { props: baseProps() })
    await wrapper.find('[data-testid="media-provider-search"]').setValue('mountains')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(api.searchProviderPhotos).toHaveBeenCalledWith('unsplash', 'ws-1', 'mountains', 1, 't')
    expect(wrapper.find('[data-testid="media-provider-result-unsplash:abc"]').exists()).toBe(true)
  })

  it('shows error state when search rejects', async () => {
    ;(api.searchProviderPhotos as unknown as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 502,
      code: 'PROVIDER_ERROR',
    })
    const wrapper = mount(MediaProviderPanel, { props: baseProps() })
    await wrapper.find('[data-testid="media-provider-search"]').setValue('mountains')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('composer.mediaPicker.provider.errorTitle')
    expect(wrapper.find('[data-testid="media-provider-result-unsplash:abc"]').exists()).toBe(false)
  })

  it('emits import-success with the asset id on successful import', async () => {
    ;(api.searchProviderPhotos as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [
        {
          externalId: 'unsplash:abc',
          previewUrl: 'https://cdn.example/abc/preview',
          fullUrl: 'https://cdn.example/abc/full',
          width: 100,
          height: 100,
          authorName: 'A',
          authorUrl: 'https://example/a',
          sourceUrl: 'https://unsplash.com/abc',
        },
      ],
      page: { number: 1, size: 20, total: 1 },
    })
    ;(api.importProviderPhoto as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      assetId: 'asset-99',
      deduped: false,
    })
    const wrapper = mount(MediaProviderPanel, { props: baseProps() })
    await wrapper.find('[data-testid="media-provider-search"]').setValue('mountains')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    await wrapper.find('[data-testid="media-provider-import-unsplash:abc"]').trigger('click')
    await flushPromises()

    expect(api.importProviderPhoto).toHaveBeenCalledWith('unsplash', 'ws-1', 'unsplash:abc', 't')
    const events = wrapper.emitted('import-success')
    expect(events).toBeTruthy()
    expect(events?.[0]).toEqual([{ assetId: 'asset-99', deduped: false }])
  })
})
