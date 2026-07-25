import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ComposerMediaPickerAsset, ComposerMediaPickerSource } from './composer-media-picker.types'

/**
 * Provides media picker state management and derived computations
 */
export function useComposerMediaPickerState(
  isOpen: boolean,
  activeSource: ComposerMediaPickerSource,
  assets: ComposerMediaPickerAsset[],
  provider?: 'unsplash' | null,
) {
  const { t } = useI18n()
  const providerQuery = ref('')

  watch(
    () => isOpen,
    (open): void => {
      if (!open) providerQuery.value = ''
    },
  )

  const selectedIds = computed(() =>
    assets.filter((asset) => asset.selected).map((asset) => asset.assetId),
  )

  const isLibrarySource = computed(() => activeSource === 'library')

  const providerEnabled = computed(() => provider === 'unsplash')

  const modalTitle = computed(() =>
    isLibrarySource.value ? t('composer.picker.header') : t('composer.picker.unsplashChip'),
  )

  const modalDescription = computed(() =>
    isLibrarySource.value
      ? t('composer.picker.libraryDescription')
      : t('composer.picker.providerDescription'),
  )

  return {
    providerQuery,
    selectedIds,
    isLibrarySource,
    providerEnabled,
    modalTitle,
    modalDescription,
  }
}
