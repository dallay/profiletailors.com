import type { MediaAssetSummary } from '@/lib/media-api'

export type ComposerMediaPickerViewState = 'loading' | 'empty' | 'error' | 'ready'
export type ComposerMediaPickerFilter = 'all' | 'image' | 'video' | 'document'

export interface ComposerMediaPickerProps {
  open: boolean
  disabled?: boolean
  state: ComposerMediaPickerViewState
  searchQuery: string
  selectedFilter: ComposerMediaPickerFilter
  filterOptions: ReadonlyArray<{ value: ComposerMediaPickerFilter; labelKey: string }>
  assets: ReadonlyArray<MediaAssetSummary>
  errorMessage?: string | null
}

export interface ComposerMediaPickerSearchChange {
  query: string
}

export interface ComposerMediaPickerFilterChange {
  filter: ComposerMediaPickerFilter
}
