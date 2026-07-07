import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ComposerMediaPickerShell from './ComposerMediaPickerShell.vue'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerCollectionState,
} from './composer-media-picker.types'

const mockT = (key: string) => {
  const translations: Record<string, string> = {
    'media.loading': 'Loading media library...',
    'media.emptyTitle': 'No media assets yet',
    'media.emptyBody': 'Upload your first image, video, or PDF to populate the library.',
    'composer.picker.header': 'Media Library',
    'composer.picker.libraryChip': 'Library',
    'composer.picker.unsplashChip': 'Unsplash',
    'composer.picker.searchPlaceholder': 'Search Unsplash',
    'composer.picker.searchAction': 'Search',
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

function makeAsset(overrides: Partial<ComposerMediaPickerAsset> = {}): ComposerMediaPickerAsset {
  return {
    assetId: 'asset-1',
    name: 'Hero image',
    mediaType: 'image/png',
    status: 'READY',
    previewUrl: '/preview/asset-1.png',
    selectable: true,
    selected: false,
    sourceType: 'UPLOADED',
    ...overrides,
  }
}

function mountShell(
  options: Partial<{
    isOpen: boolean
    collectionState: ComposerMediaPickerCollectionState
    assets: ComposerMediaPickerAsset[]
    provider: 'unsplash' | null
  }> = {},
) {
  return mount(ComposerMediaPickerShell, {
    props: {
      isOpen: true,
      collectionState: 'READY',
      assets: [],
      provider: null,
      ...options,
    },
  })
}

describe('ComposerMediaPickerShell.vue', () => {
  it('emits typed selection, apply, close, and provider-search events while keeping provider tab conditional', async () => {
    const wrapper = mountShell({
      provider: 'unsplash',
      assets: [makeAsset()],
    })

    expect(wrapper.text()).toContain('Library')
    expect(wrapper.text()).toContain('Unsplash')

    await wrapper.get('[data-testid="picker-asset-card-asset-1"]').trigger('click')
    expect(wrapper.emitted('toggle-asset')).toEqual([[{ assetId: 'asset-1' }]])

    await wrapper.get('[data-testid="picker-provider-search"] input').setValue('coffee')
    await wrapper.get('[data-testid="picker-provider-search"]').trigger('submit.prevent')
    expect(wrapper.emitted('provider-search')).toEqual([[{ query: 'coffee' }]])

    // Provider-import is emitted by the parent MediaProviderPanel via the slot,
    // not by the shell. Confirm the shell did NOT emit it directly.
    expect(wrapper.emitted('provider-import')).toBeUndefined()

    // Picker MUST stay open (no close emitted yet).
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('[data-testid="picker-apply"]').trigger('click')
    expect(wrapper.emitted('apply-selection')).toEqual([[{ assetIds: [] }]])

    await wrapper.get('[data-testid="picker-cancel"]').trigger('click')
    expect(wrapper.emitted('close')).toEqual([[]])
  })

  it('renders library collection states and ready asset fallback previews', async () => {
    const loading = mountShell({ collectionState: 'LOADING' })
    expect(loading.text()).toContain('Loading media library...')

    const empty = mountShell({ collectionState: 'EMPTY' })
    expect(empty.text()).toContain('No media assets yet')

    const error = mountShell({ collectionState: 'ERROR' })
    expect(error.text()).toContain('Unable to load media library.')

    const ready = mountShell({
      assets: [makeAsset({ assetId: 'fallback', previewUrl: null })],
    })

    const fallbackCard = ready.get('[data-testid="picker-asset-card-fallback"]')
    expect(fallbackCard.text()).toContain('No preview')
    await fallbackCard.trigger('click')
    expect(ready.emitted('toggle-asset')).toEqual([[{ assetId: 'fallback' }]])
  })

  it('keeps processing and failed assets visible but not selectable', async () => {
    const wrapper = mountShell({
      assets: [
        makeAsset({ assetId: 'processing', status: 'PROCESSING', selectable: false }),
        makeAsset({ assetId: 'failed', status: 'FAILED', selectable: false }),
      ],
    })

    expect(
      wrapper.get('[data-testid="picker-asset-card-processing"]').attributes('aria-disabled'),
    ).toBe('true')
    expect(
      wrapper.get('[data-testid="picker-asset-card-failed"]').attributes('aria-disabled'),
    ).toBe('true')

    await wrapper.get('[data-testid="picker-asset-card-processing"]').trigger('click')
    await wrapper.get('[data-testid="picker-asset-card-failed"]').trigger('click')

    expect(wrapper.emitted('toggle-asset')).toBeUndefined()
  })
})
