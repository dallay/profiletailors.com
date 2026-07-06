import type { MediaAssetSummary } from '@/lib/media-api'

export const COMPOSER_MEDIA_PICKER_VIEW_STATE = {
  LOADING: 'loading',
  EMPTY: 'empty',
  ERROR: 'error',
  READY: 'ready',
} as const

export type ComposerMediaPickerViewState =
  (typeof COMPOSER_MEDIA_PICKER_VIEW_STATE)[keyof typeof COMPOSER_MEDIA_PICKER_VIEW_STATE]

export const COMPOSER_MEDIA_PICKER_FILTER = {
  ALL: 'all',
  IMAGE: 'image',
  VIDEO: 'video',
  DOCUMENT: 'document',
} as const

export type ComposerMediaPickerFilter =
  (typeof COMPOSER_MEDIA_PICKER_FILTER)[keyof typeof COMPOSER_MEDIA_PICKER_FILTER]

export type ComposerMediaPickerFilterOption = {
  value: ComposerMediaPickerFilter
  labelKey: string
}

export const COMPOSER_MEDIA_PICKER_PROVIDER = {
  UNSPLASH: 'unsplash',
} as const

export type ComposerMediaPickerProvider =
  (typeof COMPOSER_MEDIA_PICKER_PROVIDER)[keyof typeof COMPOSER_MEDIA_PICKER_PROVIDER]

export type ComposerMediaPickerProps = {
  open: boolean
  disabled?: boolean
  state: ComposerMediaPickerViewState
  searchQuery: string
  selectedFilter: ComposerMediaPickerFilter
  filterOptions: ReadonlyArray<ComposerMediaPickerFilterOption>
  assets: ReadonlyArray<MediaAssetSummary>
  errorMessage?: string | null
  provider?: ComposerMediaPickerProvider | null
}

export type ComposerMediaPickerSearchChange = {
  query: string
}

export type ComposerMediaPickerFilterChange = {
  filter: ComposerMediaPickerFilter
}

export type ComposerMediaPickerProviderImport = {
  externalId: string
}
