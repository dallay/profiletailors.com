export type ComposerMediaPickerCollectionState = 'LOADING' | 'READY' | 'EMPTY' | 'ERROR'

export type ComposerMediaPickerAssetStatus = 'READY' | 'PROCESSING' | 'FAILED'

export interface ComposerMediaPickerAsset {
  assetId: string
  name: string
  mediaType: string
  status: ComposerMediaPickerAssetStatus
  previewUrl: string | null
  selectable: boolean
  selected: boolean
  sourceType: 'UPLOADED' | 'EXTERNAL'
}

export interface ComposerMediaPickerApplyPayload {
  assetIds: string[]
}

export interface ComposerMediaPickerTogglePayload {
  assetId: string
}

export interface ComposerMediaPickerProviderSearchPayload {
  query: string
}

export interface ComposerMediaPickerProviderImportPayload {
  externalId: string
}
