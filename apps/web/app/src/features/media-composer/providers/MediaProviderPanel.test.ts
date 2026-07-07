import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MediaProviderPanel, { type ProviderSearchResultViewModel } from './MediaProviderPanel.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

function makeResult(
  overrides: Partial<ProviderSearchResultViewModel> = {},
): ProviderSearchResultViewModel {
  return {
    externalId: 'ext-1',
    previewUrl: 'https://example.com/photo.jpg',
    name: 'Sample photo',
    authorName: 'Photographer',
    ...overrides,
  }
}

function mountPanel(propsOverride: Record<string, unknown> = {}) {
  return mount(MediaProviderPanel, {
    props: {
      results: [] as ProviderSearchResultViewModel[],
      isSearching: false,
      searchError: null,
      ...propsOverride,
    },
  })
}

describe('MediaProviderPanel.vue', () => {
  it('emits provider-search on form submit with the typed query', async () => {
    const wrapper = mountPanel()

    const input = wrapper.get('[data-testid="picker-provider-search"] input')
    await input.setValue('mountain')
    await wrapper.get('[data-testid="picker-provider-search"]').trigger('submit.prevent')

    expect(wrapper.emitted('provider-search')).toEqual([[{ query: 'mountain' }]])
  })

  it('emits provider-import with the clicked result externalId and does not refetch', async () => {
    const wrapper = mountPanel({
      results: [makeResult({ externalId: 'ext-22', name: 'A photo' })],
    })

    await wrapper.get('[data-testid="provider-panel-import"]').trigger('click')

    const emissions = wrapper.emitted('provider-import') ?? []
    expect(emissions.length).toBe(1)
    expect(emissions[0]).toEqual([{ externalId: 'ext-22' }])
  })

  it('renders empty state when no results have been fetched yet', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="provider-panel-empty"]').exists()).toBe(true)
  })

  it('renders error state when the parent surfaces a search error', () => {
    const wrapper = mountPanel({ searchError: 'Search failed: rate limited' })
    const errorEl = wrapper.find('[data-testid="provider-panel-search-error"]')
    expect(errorEl.exists()).toBe(true)
    expect(errorEl.text()).toContain('Search failed: rate limited')
  })
})
