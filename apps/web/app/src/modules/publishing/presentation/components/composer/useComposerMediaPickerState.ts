import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useI18n } from 'vue-i18n'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerSource,
} from './composer-media-picker.types'

/**
 * Provides media picker state management and derived computations
 */
export function useComposerMediaPickerState(
  isOpen: MaybeRefOrGetter<boolean>,
  activeSource: MaybeRefOrGetter<ComposerMediaPickerSource>,
  assets: MaybeRefOrGetter<ComposerMediaPickerAsset[]>,
  provider?: MaybeRefOrGetter<'unsplash' | null>,
) {
  const { t } = useI18n()
  const providerQuery = ref('')

  watch(
    () => toValue(isOpen),
    (open): void => {
      if (!open) providerQuery.value = ''
    },
  )

  const selectedIds = computed(() =>
    toValue(assets)
      .filter((asset) => asset.selected)
      .map((asset) => asset.assetId),
  )

  const isLibrarySource = computed(() => toValue(activeSource) === 'library')

  const providerEnabled = computed(() => toValue(provider) === 'unsplash')

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
