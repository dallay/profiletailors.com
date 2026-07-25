import { computed, ref } from 'vue'
import type { MediaAsset } from '@modules/media/infrastructure/media.store'

export function useMediaLibrarySelection(visibleAssets: globalThis.Ref<MediaAsset[]>) {
  const selectedAssetIds = ref<string[]>([])

  const allVisibleSelected = computed(() =>
    visibleAssets.value.length > 0 && visibleAssets.value.every((a) => selectedAssetIds.value.includes(a.assetId)),
  )

  const hasSelection = computed(() => selectedAssetIds.value.length > 0)

  const toggleAssetSelection = (assetId: string) => {
    if (selectedAssetIds.value.includes(assetId)) {
      selectedAssetIds.value = selectedAssetIds.value.filter((id) => id !== assetId)
      return
    }
    selectedAssetIds.value = [...selectedAssetIds.value, assetId]
  }

  const clearSelection = () => {
    selectedAssetIds.value = []
  }

  const toggleSelectAllVisible = () => {
    if (allVisibleSelected.value) {
      clearSelection()
      return
    }
    selectedAssetIds.value = visibleAssets.value
      .filter((a) => a.status === 'READY' || a.status === 'FAILED')
      .map((a) => a.assetId)
  }

  const removeSelectedAsset = (assetId: string) => {
    selectedAssetIds.value = selectedAssetIds.value.filter((id) => id !== assetId)
  }

  return {
    selectedAssetIds,
    allVisibleSelected,
    hasSelection,
    toggleAssetSelection,
    clearSelection,
    toggleSelectAllVisible,
    removeSelectedAsset,
  }
}
