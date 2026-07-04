import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { MediaAssetSummary } from '@/lib/media-api'
import {
  COMPOSER_MEDIA_PICKER_FILTER,
  COMPOSER_MEDIA_PICKER_VIEW_STATE,
  type ComposerMediaPickerFilter,
  type ComposerMediaPickerFilterOption,
  type ComposerMediaPickerViewState,
} from './composer-media-picker.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
}))

vi.mock('@/components/ui/dialog', () => ({
  Dialog: { template: '<div data-testid="dialog"><slot /></div>', props: ['open'] },
  DialogContent: {
    template: '<div data-testid="dialog-content"><slot /></div>',
    props: ['class', 'showCloseButton'],
  },
  DialogHeader: { template: '<div data-testid="dialog-header"><slot /></div>' },
  DialogTitle: { template: '<h2 data-testid="dialog-title"><slot /></h2>' },
  DialogDescription: { template: '<p data-testid="dialog-description"><slot /></p>' },
}))

vi.mock('@/components/ui/button', () => ({
  Button: {
    template: '<button data-slot="button"><slot /></button>',
    props: ['variant', 'size', 'disabled', 'ariaLabel', 'type'],
  },
}))

vi.mock('@/components/ui/select', () => ({
  Select: {
    name: 'Select',
    template: '<div><slot /></div>',
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue'],
  },
  SelectTrigger: {
    template: '<button type="button" :disabled="disabled"><slot /></button>',
    props: ['disabled'],
  },
  SelectValue: { template: '<span />' },
  SelectContent: { template: '<div><slot /></div>' },
  SelectItem: { template: '<button type="button"><slot /></button>', props: ['value'] },
}))

vi.mock('@/components/ui/input', () => ({
  Input: {
    template:
      '<input data-slot="input" :value="modelValue" :disabled="disabled" :placeholder="placeholder" :aria-label="ariaLabel" @input="onInput" />',
    props: ['modelValue', 'disabled', 'placeholder', 'ariaLabel', 'type', 'class'],
    emits: ['update:modelValue'],
    setup(
      _props: unknown,
      { emit }: { emit: (event: 'update:modelValue', value: string) => void },
    ) {
      return {
        onInput(event: Event) {
          const target = event.target as HTMLInputElement | null
          emit('update:modelValue', target?.value ?? '')
        },
      }
    },
  },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return { XIcon: stub, Search: stub, X: stub }
})

// Lazy import so mocks are active
const { default: ComposerMediaPickerShell } = await import('./ComposerMediaPickerShell.vue')

// ---------------------------------------------------------------------------
// Test data factories
// ---------------------------------------------------------------------------

function makeAsset(overrides: Partial<MediaAssetSummary> = {}): MediaAssetSummary {
  return {
    assetId: 'asset-1',
    workspaceId: 'ws-1',
    sourceType: 'UPLOADED',
    mediaType: 'image/png',
    status: 'READY',
    originalFilename: 'photo.png',
    fileSizeBytes: 1024,
    createdAt: '2026-06-01T12:00:00Z',
    ...overrides,
  }
}

const DEFAULT_FILTER_OPTIONS: ReadonlyArray<ComposerMediaPickerFilterOption> = [
  { value: COMPOSER_MEDIA_PICKER_FILTER.ALL, labelKey: 'composer.mediaPicker.filterAll' },
  { value: COMPOSER_MEDIA_PICKER_FILTER.IMAGE, labelKey: 'composer.mediaPicker.filterImage' },
  { value: COMPOSER_MEDIA_PICKER_FILTER.VIDEO, labelKey: 'composer.mediaPicker.filterVideo' },
  { value: COMPOSER_MEDIA_PICKER_FILTER.DOCUMENT, labelKey: 'composer.mediaPicker.filterDocument' },
]

function mountShell(overrides: Record<string, unknown> = {}) {
  return mount(ComposerMediaPickerShell, {
    attachTo: document.body,
    props: {
      open: true,
      state: COMPOSER_MEDIA_PICKER_VIEW_STATE.READY as ComposerMediaPickerViewState,
      searchQuery: '',
      selectedFilter: COMPOSER_MEDIA_PICKER_FILTER.ALL as ComposerMediaPickerFilter,
      filterOptions: DEFAULT_FILTER_OPTIONS,
      assets: [makeAsset()],
      ...overrides,
    },
    global: {
      mocks: { $t: (key: string) => key },
    },
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ComposerMediaPickerShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('stops Escape from bubbling to the parent composer modal', async () => {
    const wrapper = mountShell()
    const parentKeydown = vi.fn()
    document.addEventListener('keydown', parentKeydown)

    try {
      await wrapper.get('[data-testid="media-picker-close"]').trigger('keydown', { key: 'Escape' })
      expect(wrapper.emitted('close')).toHaveLength(1)
      expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
      expect(parentKeydown).not.toHaveBeenCalled()
    } finally {
      document.removeEventListener('keydown', parentKeydown)
    }
  })

  // ── Localized accessible names ──────────────────────────────────────────

  describe('localized accessible names', () => {
    it('renders the dialog with the localized title key', () => {
      const wrapper = mountShell()
      expect(wrapper.get('[data-testid="dialog-title"]').text()).toBe('composer.mediaPicker.title')
    })

    it('renders the localized description', () => {
      const wrapper = mountShell()
      expect(wrapper.get('[data-testid="dialog-description"]').text()).toBe(
        'composer.mediaPicker.description',
      )
    })

    it('renders search input with localized aria-label', () => {
      const wrapper = mountShell()
      const searchInput = wrapper.get('[data-testid="media-picker-search"]')
      expect(searchInput.attributes('aria-label')).toBe('composer.mediaPicker.searchLabel')
    })

    it('renders filter select with localized aria-label', () => {
      const wrapper = mountShell()
      const filter = wrapper.get('[data-testid="media-picker-filter"]')
      expect(filter.attributes('aria-label')).toBe('composer.mediaPicker.filterLabel')
    })

    it('renders asset grid region with localized label', () => {
      const wrapper = mountShell()
      const region = wrapper.get('[data-testid="media-picker-asset-grid"]')
      expect(region.element.tagName.toLowerCase()).toBe('section')
      expect(region.attributes('aria-label')).toBe('composer.mediaPicker.assetGridLabel')
    })
  })

  // ── Deterministic view states ───────────────────────────────────────────

  describe('deterministic view states', () => {
    it('renders loading state with localized message', () => {
      const wrapper = mountShell({ state: 'loading' })
      expect(wrapper.text()).toContain('composer.mediaPicker.loading')
      expect(wrapper.find('[data-testid="media-picker-asset-grid"]').exists()).toBe(false)
    })

    it('announces non-ready state panel changes politely', () => {
      const wrapper = mountShell({ state: 'loading' })
      const liveRegion = wrapper.get('[aria-live="polite"]')
      expect(liveRegion.attributes('aria-atomic')).toBe('true')
      expect(liveRegion.text()).toContain('composer.mediaPicker.loading')
    })

    it('renders empty state with localized title and body', () => {
      const wrapper = mountShell({ state: 'empty', assets: [] })
      expect(wrapper.text()).toContain('composer.mediaPicker.emptyTitle')
      expect(wrapper.text()).toContain('composer.mediaPicker.emptyBody')
    })

    it('renders error state with localized title and custom error message', () => {
      const wrapper = mountShell({ state: 'error', errorMessage: 'Network failure' })
      expect(wrapper.text()).toContain('composer.mediaPicker.errorTitle')
      expect(wrapper.text()).toContain('Network failure')
    })

    it('renders error state with fallback body when no errorMessage', () => {
      const wrapper = mountShell({ state: 'error' })
      expect(wrapper.text()).toContain('composer.mediaPicker.errorBody')
    })

    it('renders ready state with asset grid', () => {
      const wrapper = mountShell({ state: 'ready', assets: [makeAsset()] })
      const grid = wrapper.find('[data-testid="media-picker-asset-grid"]')
      expect(grid.exists()).toBe(true)
    })
  })

  // ── Disabled state suppression ──────────────────────────────────────────

  describe('disabled state', () => {
    it('renders disabled state localized message', () => {
      const wrapper = mountShell({ disabled: true })
      expect(wrapper.text()).toContain('composer.mediaPicker.disabledTitle')
      expect(wrapper.text()).toContain('composer.mediaPicker.disabledBody')
    })

    it('disables search input when disabled', () => {
      const wrapper = mountShell({ disabled: true })
      const search = wrapper.find('[data-testid="media-picker-search"]')
      expect(search.attributes('disabled')).toBeDefined()
    })

    it('disables filter select when disabled', () => {
      const wrapper = mountShell({ disabled: true })
      const filter = wrapper.find('[data-testid="media-picker-filter"]')
      expect(filter.attributes('disabled')).toBeDefined()
    })

    it('does not emit search-change when disabled', async () => {
      const wrapper = mountShell({ disabled: true })
      const search = wrapper.find('[data-testid="media-picker-search"]')
      await search.setValue('test')
      expect(wrapper.emitted('search-change')).toBeUndefined()
    })

    it('does not emit filter-change when disabled', async () => {
      const wrapper = mountShell({ disabled: true })
      wrapper.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 'image')
      expect(wrapper.emitted('filter-change')).toBeUndefined()
    })
  })

  // ── Ready-state asset rendering ─────────────────────────────────────────

  describe('ready-state asset rendering', () => {
    it('renders an asset card for each provided asset', () => {
      const assets = [
        makeAsset({ assetId: 'a1', originalFilename: 'photo.png' }),
        makeAsset({ assetId: 'a2', originalFilename: 'clip.mp4', mediaType: 'video/mp4' }),
      ]
      const wrapper = mountShell({ state: 'ready', assets })
      const cards = wrapper.findAll('[data-testid^="media-picker-asset-a"]')
      expect(cards).toHaveLength(2)
    })

    it('displays asset filename in card', () => {
      const wrapper = mountShell({
        state: 'ready',
        assets: [makeAsset({ assetId: 'a1', originalFilename: 'holiday.jpg' })],
      })
      expect(wrapper.text()).toContain('holiday.jpg')
    })
  })

  // ── Typed event emissions ───────────────────────────────────────────────

  describe('typed event emissions', () => {
    it('emits close when close button is clicked', async () => {
      const wrapper = mountShell()
      const closeBtn = wrapper.get('[data-testid="media-picker-close"]')
      await closeBtn.trigger('click')
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('emits update:open(false) when close button is clicked', async () => {
      const wrapper = mountShell()
      const closeBtn = wrapper.get('[data-testid="media-picker-close"]')
      await closeBtn.trigger('click')
      expect(wrapper.emitted('update:open')).toBeTruthy()
      expect(wrapper.emitted('update:open')![0]).toEqual([false])
    })

    it('emits search-change with typed payload when search changes', async () => {
      const wrapper = mountShell()
      const search = wrapper.get('[data-testid="media-picker-search"]')
      await search.setValue('landscape')
      await flushPromises()
      const events = wrapper.emitted('search-change')
      expect(events).toBeTruthy()
      expect(events![events!.length - 1]).toEqual([{ query: 'landscape' }])
    })

    it('emits filter-change with typed payload when filter changes', async () => {
      const wrapper = mountShell()
      wrapper.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 'video')
      const events = wrapper.emitted('filter-change')
      expect(events).toBeTruthy()
      expect(events![events!.length - 1]).toEqual([{ filter: 'video' }])
    })
  })
})

describe('ComposerMediaPickerShell — real dialog keyboard behavior', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.doUnmock('@/components/ui/dialog')
    vi.doUnmock('@/components/ui/button')
    vi.doUnmock('@/components/ui/input')
    vi.doUnmock('@lucide/vue')
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('keeps keyboard focus within the real dialog controls until Escape dismisses it', async () => {
    const { default: RealDialogShell } = await import('./ComposerMediaPickerShell.vue')
    const wrapper = mount(RealDialogShell, {
      attachTo: document.body,
      props: {
        open: true,
        state: 'ready' as ComposerMediaPickerViewState,
        searchQuery: '',
        selectedFilter: 'all' as ComposerMediaPickerFilter,
        filterOptions: DEFAULT_FILTER_OPTIONS,
        assets: [makeAsset()],
      },
    })
    await flushPromises()

    const closeButton = document.body.querySelector<HTMLButtonElement>(
      '[data-testid="media-picker-close"]',
    )
    const searchInput = document.body.querySelector<HTMLInputElement>(
      '[data-testid="media-picker-search"]',
    )
    const filterSelect = document.body.querySelector<HTMLSelectElement>(
      '[data-testid="media-picker-filter"]',
    )
    expect(closeButton).not.toBeNull()
    expect(searchInput).not.toBeNull()
    expect(filterSelect).not.toBeNull()

    closeButton!.focus()
    expect(document.activeElement).toBe(closeButton)
    closeButton!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    await flushPromises()
    expect([closeButton, searchInput, filterSelect]).toContain(document.activeElement)

    closeButton!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })
})
