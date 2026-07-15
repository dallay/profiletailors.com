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
  it('emits provider-import with the clicked result externalId and does not refetch', async () => {
    const wrapper = mountPanel({
      results: [makeResult({ externalId: 'ext-22', name: 'A photo' })],
    })

    await wrapper.get('[data-testid="provider-panel-import"]').trigger('click')

    const emissions = wrapper.emitted('provider-import') ?? []
    expect(emissions.length).toBe(1)
    expect(emissions[0]).toEqual([{ externalId: 'ext-22' }])
  })

  it('does not emit a second provider-import when the result is already selectedForImport', async () => {
    const result = makeResult({ externalId: 'ext-22', name: 'A photo' })
    const wrapper = mountPanel({ results: [result] })

    // First click: emits.
    await wrapper.get('[data-testid="provider-panel-import"]').trigger('click')
    expect(wrapper.emitted('provider-import')?.length).toBe(1)

    // Parent marks result as selectedForImport (simulates in-flight state).
    await wrapper.setProps({ results: [{ ...result, selectedForImport: true }] })

    // Second click: guarded by selectedForImport guard — no second emit.
    await wrapper.get('[data-testid="provider-panel-import"]').trigger('click')
    expect(wrapper.emitted('provider-import')?.length).toBe(1)
  })

  it('keeps an imported result disabled and renders the added state', async () => {
    const wrapper = mountPanel({
      results: [makeResult({ imported: true })],
    })

    const button = wrapper.get<HTMLButtonElement>('[data-testid="provider-panel-import"]')
    expect(button.element.disabled).toBe(true)
    expect(button.text()).toContain('composer.picker.importedAction')
    await button.trigger('click')
    expect(wrapper.emitted('provider-import')).toBeUndefined()
  })

  it('renders compliant photographer and Unsplash attribution links', () => {
    const wrapper = mountPanel({
      results: [
        makeResult({
          authorUrl: 'https://unsplash.com/@photographer',
          sourceUrl: 'https://unsplash.com/photos/ext-1',
        }),
      ],
    })

    const links = wrapper.findAll('a')
    expect(links.map((link) => link.attributes('href'))).toEqual([
      'https://unsplash.com/@photographer',
      'https://unsplash.com/photos/ext-1',
    ])
    expect(links.every((link) => link.attributes('rel') === 'noopener noreferrer')).toBe(true)
  })

  it('renders empty state when no results have been fetched yet', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="provider-panel-empty"]').exists()).toBe(true)
  })

  it('renders loading state while the parent is searching', () => {
    const wrapper = mountPanel({ isSearching: true })
    expect(wrapper.find('[data-testid="provider-panel-loading"]').exists()).toBe(true)
  })

  it('renders error state when the parent surfaces a search error', () => {
    const wrapper = mountPanel({ searchError: 'Search failed: rate limited' })
    const errorEl = wrapper.find('[data-testid="provider-panel-search-error"]')
    expect(errorEl.exists()).toBe(true)
    expect(errorEl.text()).toContain('Search failed: rate limited')
  })

  it('renders the imported-in-progress state while a result is selected for import', async () => {
    const wrapper = mountPanel({
      results: [makeResult({ selectedForImport: true })],
    })

    const button = wrapper.get<HTMLButtonElement>('[data-testid="provider-panel-import"]')
    expect(button.element.disabled).toBe(true)
    expect(button.text()).toContain('composer.picker.importingAction')
    await button.trigger('click')
    expect(wrapper.emitted('provider-import')).toBeUndefined()
  })

  it('renders fallback preview and attribution text when provider URLs are missing', () => {
    const wrapper = mountPanel({
      results: [
        makeResult({
          previewUrl: null,
          authorName: 'Plain Author',
          authorUrl: null,
          sourceUrl: null,
        }),
      ],
    })

    expect(wrapper.text()).toContain('composer.picker.noPreview')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findAll('a')).toEqual([])
    expect(wrapper.text()).toContain('Plain Author')
    expect(wrapper.text()).toContain('Unsplash')
  })

  it('labels the provider search section for assistive technology', () => {
    const wrapper = mountPanel()

    expect(wrapper.get('[data-testid="provider-panel"]').attributes('aria-label')).toBe(
      'composer.picker.providerSearchLabel',
    )
  })

  it('shows the error state instead of results when provider search fails', () => {
    const wrapper = mountPanel({
      results: [makeResult()],
      searchError: 'Import failed: try again',
    })

    expect(wrapper.find('[data-testid="provider-panel-search-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="provider-result-ext-1"]').exists()).toBe(false)
  })

  it('renders provider results sorted by name', () => {
    const wrapper = mountPanel({
      results: [
        makeResult({ externalId: 'ext-b', name: 'Zulu photo' }),
        makeResult({ externalId: 'ext-a', name: 'Alpha photo' }),
      ],
    })

    const cards = wrapper.findAll('[data-testid^="provider-result-"]')
    expect(cards[0]?.text()).toContain('Alpha photo')
    expect(cards[1]?.text()).toContain('Zulu photo')
  })
})
