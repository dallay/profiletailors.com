import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ComposerMediaPickerShell from './ComposerMediaPickerShell.vue'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerCollectionState,
  ComposerMediaPickerSource,
} from './composer-media-picker.types'

const mockT = (key: string) => {
  const translations: Record<string, string> = {
    'media.loading': 'Loading media library...',
    'media.emptyTitle': 'No media assets yet',
    'media.emptyBody': 'Upload your first image, video, or PDF to populate the library.',
    'media.noFilteredAssetsTitle': 'No assets match the current filters',
    'media.noFilteredAssetsBody':
      'Try a different search or change the filters to see more results.',
    'composer.picker.header': 'Media Library',
    'composer.picker.libraryChip': 'Library',
    'composer.picker.unsplashChip': 'Unsplash',
    'composer.picker.searchPlaceholder': 'Search Unsplash',
    'composer.picker.searchAction': 'Search',
    'composer.picker.searchingAction': 'Searching…',
    'composer.picker.providerSearchLabel': 'Search Unsplash',
    'composer.picker.librarySearchLabel': 'Search workspace media',
    'composer.picker.librarySearchPlaceholder': 'Filename, asset ID, or MIME type',
    'composer.picker.libraryDescription':
      'Browse images and videos already saved in this workspace.',
    'composer.picker.providerDescription':
      'Search Unsplash and import media into this post without leaving the composer.',
    'composer.picker.errorLoad': 'Unable to load media library.',
    'composer.picker.noPreview': 'No preview',
    'composer.picker.cancel': 'Cancel',
    'composer.picker.apply': 'Apply',
  }

  return translations[key] ?? key
}

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: mockT }),
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Check: stub,
    Search: stub,
    X: stub,
  }
})

vi.mock('@/components/ui/dialog', () => ({
  Dialog: {
    props: ['open'],
    emits: ['update:open'],
    template: '<div v-if="open"><slot /></div>',
  },
  DialogContent: {
    template: '<div data-testid="composer-media-picker-shell"><slot /></div>',
  },
  DialogDescription: {
    template: '<p><slot /></p>',
  },
  DialogTitle: {
    template: '<h3><slot /></h3>',
  },
}))

function makeAsset(overrides: Partial<ComposerMediaPickerAsset> = {}): ComposerMediaPickerAsset {
  return {
    assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
    name: 'Hero image',
    mediaType: 'image/png',
    status: 'READY',
    previewUrl: '/preview/8bdec504-2ed1-59e4-8a4a-c068789d03d2.png',
    selectable: true,
    selected: false,
    sourceType: 'UPLOADED',
    ...overrides,
  }
}

function mountShell(
  options: Partial<{
    isOpen: boolean
    activeSource: ComposerMediaPickerSource
    collectionState: ComposerMediaPickerCollectionState
    assets: ComposerMediaPickerAsset[]
    selectedAssetIds: string[]
    provider: 'unsplash' | null
  }> = {},
) {
  const assets = options.assets ?? []

  return mount(ComposerMediaPickerShell, {
    props: {
      isOpen: true,
      activeSource: 'library',
      collectionState: 'READY',
      assets,
      provider: null,
      ...options,
      selectedAssetIds:
        options.selectedAssetIds ??
        assets.filter((asset) => asset.selected).map((asset) => asset.assetId),
    },
  })
}

describe('ComposerMediaPickerShell.vue', () => {
  it('renders the source switcher and emits library selection/apply/close events', async () => {
    const wrapper = mountShell({
      provider: 'unsplash',
      assets: [makeAsset({ selected: true })],
    })

    expect(wrapper.text()).toContain('Library')
    expect(wrapper.text()).toContain('Unsplash')

    await wrapper
      .get('[data-testid="picker-asset-card-8bdec504-2ed1-59e4-8a4a-c068789d03d2"]')
      .trigger('click')
    expect(wrapper.emitted('toggle-asset')).toEqual([
      [{ assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2' }],
    ])

    expect(
      wrapper
        .find(
          '[data-testid="picker-asset-selected-indicator-8bdec504-2ed1-59e4-8a4a-c068789d03d2"]',
        )
        .exists(),
    ).toBe(true)

    await wrapper.get('[data-testid="picker-apply"]').trigger('click')
    expect(wrapper.emitted('apply-selection')).toEqual([
      [{ assetIds: ['8bdec504-2ed1-59e4-8a4a-c068789d03d2'] }],
    ])

    await wrapper.get('[data-testid="picker-cancel"]').trigger('click')
    expect(wrapper.emitted('close')).toEqual([[]])
  })

  it('switches to the provider source and emits typed provider search events', async () => {
    const wrapper = mountShell({
      provider: 'unsplash',
      activeSource: 'unsplash',
    })

    expect(wrapper.text()).toContain('Search Unsplash')

    await wrapper.get('[data-testid="picker-provider-search"] input').setValue('coffee')
    await wrapper.get('[data-testid="picker-provider-search"]').trigger('submit.prevent')
    expect(wrapper.emitted('provider-search')).toEqual([[{ query: 'coffee' }]])

    await wrapper.get('[data-testid="picker-source-library"]').trigger('click')
    expect(wrapper.emitted('set-active-source')).toEqual([[{ source: 'library' }]])
  })

  it('shows an accessible library search and emits trimmed queries while typing', async () => {
    const wrapper = mountShell({ provider: 'unsplash' })

    const searchInput = wrapper.get('[data-testid="picker-library-search"] input')
    expect(searchInput.attributes('type')).toBe('search')
    expect(wrapper.get('label[for="picker-library-query"]').text()).toBe('Search workspace media')
    expect(searchInput.attributes('placeholder')).toBe('Filename, asset ID, or MIME type')
    expect(wrapper.find('[data-testid="picker-provider-search"]').exists()).toBe(false)

    await searchInput.setValue('  hero  ')
    expect(wrapper.emitted('library-search')).toEqual([[{ query: 'hero' }]])

    await wrapper.get('[data-testid="picker-source-unsplash"]').trigger('click')
    await wrapper.setProps({ activeSource: 'unsplash' })
    expect(wrapper.find('[data-testid="picker-library-search"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="picker-provider-search"]').exists()).toBe(true)
  })

  it('provides a visible focus indicator for keyboard users', () => {
    const wrapper = mountShell()
    const searchInput = wrapper.get('[data-testid="picker-library-search"] input')

    expect(searchInput.classes()).toContain('focus-visible:ring-2')
    expect(searchInput.classes()).toContain('focus-visible:ring-text-display')
  })

  it('shows a filtered-empty state when the active library query has no assets', async () => {
    const wrapper = mountShell({ collectionState: 'READY' })

    await wrapper.get('[data-testid="picker-library-search"] input').setValue('missing')

    expect(wrapper.text()).toContain('No assets match the current filters')
    expect(wrapper.text()).toContain(
      'Try a different search or change the filters to see more results.',
    )
    expect(wrapper.text()).not.toContain('No media assets yet')
  })

  it('applies selected assets that are hidden by the current search', async () => {
    const wrapper = mountShell({
      assets: [makeAsset({ assetId: '3d16f71a-564f-5dea-bc04-9d412a3bf253', selected: true })],
      selectedAssetIds: ['3d16f71a-564f-5dea-bc04-9d412a3bf253', 'hidden'],
    })

    await wrapper.get('[data-testid="picker-apply"]').trigger('click')

    expect(wrapper.emitted('apply-selection')).toEqual([
      [{ assetIds: ['3d16f71a-564f-5dea-bc04-9d412a3bf253', 'hidden'] }],
    ])
  })

  it('clears the library search when the picker closes', async () => {
    const wrapper = mountShell()

    await wrapper.get('[data-testid="picker-library-search"] input').setValue('hero')
    await wrapper.setProps({ isOpen: false })
    await wrapper.setProps({ isOpen: true })

    expect(wrapper.get('[data-testid="picker-library-search"] input').element).toHaveProperty(
      'value',
      '',
    )
    expect(wrapper.emitted('library-search')).toEqual([[{ query: 'hero' }], [{ query: '' }]])
  })

  it('renders library collection states and fallback previews', async () => {
    const loading = mountShell({ collectionState: 'LOADING' })
    expect(loading.text()).toContain('Loading media library...')

    const empty = mountShell({ collectionState: 'EMPTY' })
    expect(empty.text()).toContain('No media assets yet')

    const error = mountShell({ collectionState: 'ERROR' })
    expect(error.text()).toContain('Unable to load media library.')

    const ready = mountShell({
      assets: [makeAsset({ assetId: '222c393c-7f87-5577-beaa-2e4e4c3c5cd1', previewUrl: null })],
    })

    const fallbackCard = ready.get(
      '[data-testid="picker-asset-card-222c393c-7f87-5577-beaa-2e4e4c3c5cd1"]',
    )
    expect(fallbackCard.text()).toContain('No preview')
    await fallbackCard.trigger('click')
    expect(ready.emitted('toggle-asset')).toEqual([
      [{ assetId: '222c393c-7f87-5577-beaa-2e4e4c3c5cd1' }],
    ])
  })

  it('keeps processing and failed assets visible but not selectable', async () => {
    const wrapper = mountShell({
      assets: [
        makeAsset({
          assetId: 'a5a01d7b-50a7-516e-a439-b3478650b956',
          status: 'PROCESSING',
          selectable: false,
        }),
        makeAsset({
          assetId: 'b5c006d8-273f-5a30-840e-ae1830fd989b',
          status: 'FAILED',
          selectable: false,
        }),
      ],
    })

    expect(
      wrapper
        .get('[data-testid="picker-asset-card-a5a01d7b-50a7-516e-a439-b3478650b956"]')
        .attributes('aria-disabled'),
    ).toBe('true')
    expect(
      wrapper
        .get('[data-testid="picker-asset-card-b5c006d8-273f-5a30-840e-ae1830fd989b"]')
        .attributes('aria-disabled'),
    ).toBe('true')

    await wrapper
      .get('[data-testid="picker-asset-card-a5a01d7b-50a7-516e-a439-b3478650b956"]')
      .trigger('click')
    await wrapper
      .get('[data-testid="picker-asset-card-b5c006d8-273f-5a30-840e-ae1830fd989b"]')
      .trigger('click')

    expect(wrapper.emitted('toggle-asset')).toBeUndefined()
  })
})
