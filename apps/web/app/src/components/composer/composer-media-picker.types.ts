export type ComposerMediaPickerCollectionState = 'LOADING' | 'READY' | 'EMPTY' | 'ERROR'
export type ComposerMediaPickerSource = 'library' | 'unsplash'

export type ComposerMediaPickerAssetStatus = 'READY' | 'PROCESSING' | 'FAILED'

export type ComposerMediaPickerAsset = {
  assetId: string
  name: string
  mediaType: string
  status: ComposerMediaPickerAssetStatus
  previewUrl: string | null
  selectable: boolean
  selected: boolean
  sourceType: 'UPLOADED' | 'EXTERNAL'
}

export type ComposerMediaPickerApplyPayload = {
  assetIds: string[]
}

export type ComposerMediaPickerTogglePayload = {
  assetId: string
}

export type ComposerMediaPickerProviderSearchPayload = {
  query: string
}

export type ComposerMediaPickerProviderImportPayload = {
  externalId: string
}
