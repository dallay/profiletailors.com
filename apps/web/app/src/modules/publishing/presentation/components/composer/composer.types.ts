export type ComposerScheduleMode = 'now' | 'next' | 'custom'

export type ComposerInlineAttachment =
  | {
      key: string
      kind: 'draft'
      assetId: string
      name: string
      previewUrl: string | null
      isUploading: false
      uploadProgress: 100
      uploadStateLabel: null
    }
  | {
      key: string
      kind: 'local-upload'
      assetId: null
      name: string
      previewUrl: string | null
      isUploading: boolean
      uploadProgress: number | null
      uploadStateLabel: string | null
    }
