import { ref, computed } from 'vue'
import { useMediaStore } from '@modules/media'
import type { ComposerInlineAttachment } from '@modules/publishing/presentation/components/composer/composer.types'

const COMPOSER_SUPPORTED_MEDIA_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'video/mp4',
])

export function useComposerMediaUpload() {
  const mediaStore = useMediaStore()

  // Upload state
  const uploadPreviewBlob = ref<string | null>(null)
  const selectedUploadFile = ref<File | null>(null)
  const uploadTempKey = ref<string | null>(null)
  const uploadProgress = ref(0)
  const isLocalUploadInFlight = ref(false)

  // Helpers
  function clearUploadPreviewBlob() {
    if (uploadPreviewBlob.value) {
      URL.revokeObjectURL(uploadPreviewBlob.value)
      uploadPreviewBlob.value = null
    }
  }

  function addFiles(filesList: File[]): boolean {
    const file = filesList.find((file) => {
      const isSupported = COMPOSER_SUPPORTED_MEDIA_TYPES.has(file.type)
      const isUnderLimit = file.size <= 10 * 1024 * 1024 // 10MB
      if (!isSupported) alert('Unsupported media format. Supported formats: JPEG, PNG, WEBP, GIF, MP4.')
      if (!isUnderLimit) alert('File size exceeds 10MB limit.')
      return isSupported && isUnderLimit
    })

    if (!file) return false

    clearUploadPreviewBlob()
    selectedUploadFile.value = file
    uploadPreviewBlob.value = URL.createObjectURL(file)
    uploadTempKey.value = `modal-upload-${Date.now()}`
    uploadProgress.value = 0
    isLocalUploadInFlight.value = false

    return true
  }

  async function uploadAndTrack(file: File, onProgress?: (pct: number) => void): Promise<boolean> {
    clearUploadPreviewBlob()
    uploadPreviewBlob.value = URL.createObjectURL(file)
    uploadProgress.value = 0

    const tempKey = `modal-upload-${Date.now()}`
    uploadTempKey.value = tempKey

    try {
      const asset = await mediaStore.createAndUpload(file, tempKey, (pct) => {
        uploadProgress.value = pct
        onProgress?.(pct)
      })

      mediaStore.addToSelection(asset.assetId)
      uploadTempKey.value = null
      uploadProgress.value = 0

      return true
    } catch {
      uploadTempKey.value = null
      uploadProgress.value = 0
      return false
    }
  }

  function removeFile() {
    clearUploadPreviewBlob()
    selectedUploadFile.value = null
    if (uploadTempKey.value) {
      mediaStore.dismissUpload(uploadTempKey.value)
    }
    uploadTempKey.value = null
    uploadProgress.value = 0
    isLocalUploadInFlight.value = false
  }

  function extractFilesFromDataTransfer(dataTransfer: DataTransfer | null): File[] {
    if (!dataTransfer) return []
    return Array.from(dataTransfer.files ?? [])
  }

  function extractFilesFromClipboard(clipboardData: DataTransfer | null): File[] {
    if (!clipboardData) return []

    const clipboardFiles = Array.from(clipboardData.files ?? [])
    if (clipboardFiles.length > 0) return clipboardFiles

    return Array.from(clipboardData.items ?? [])
      .filter((item) => item.kind === 'file')
      .map((item) => item.getAsFile())
      .filter((file): file is File => file !== null)
  }

  return {
    // State
    uploadPreviewBlob,
    selectedUploadFile,
    uploadTempKey,
    uploadProgress,
    isLocalUploadInFlight,

    // Methods
    clearUploadPreviewBlob,
    addFiles,
    uploadAndTrack,
    removeFile,
    extractFilesFromDataTransfer,
    extractFilesFromClipboard,
  }
}
