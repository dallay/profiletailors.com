<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, Search, X } from '@lucide/vue'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerApplyPayload,
  ComposerMediaPickerCollectionState,
  ComposerMediaPickerProviderSearchPayload,
  ComposerMediaPickerSource,
  ComposerMediaPickerTogglePayload,
} from './composer-media-picker.types'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
    activeSource: ComposerMediaPickerSource
    collectionState: ComposerMediaPickerCollectionState
    assets: ComposerMediaPickerAsset[]
    provider?: 'unsplash' | null
    applyDisabled?: boolean
    applyDisabledMessage?: string | null
  }>(),
  {
    provider: null,
    applyDisabled: false,
    applyDisabledMessage: null,
  },
)

const emit = defineEmits<{
  (e: 'toggle-asset', payload: ComposerMediaPickerTogglePayload): void
  (e: 'apply-selection', payload: ComposerMediaPickerApplyPayload): void
  (e: 'close'): void
  (e: 'provider-search', payload: ComposerMediaPickerProviderSearchPayload): void
  (e: 'set-active-source', payload: { source: ComposerMediaPickerSource }): void
}>()

const { t } = useI18n()
const providerQuery = ref('')

watch(
  () => props.isOpen,
  (isOpen) => {
    if (!isOpen) providerQuery.value = ''
  },
)

const selectedIds = computed(() => props.assets.filter((asset) => asset.selected).map((asset) => asset.assetId))
const isLibrarySource = computed(() => props.activeSource === 'library')
const providerEnabled = computed(() => props.provider === 'unsplash')
const modalTitle = computed(() =>
  isLibrarySource.value ? t('composer.picker.header') : t('composer.picker.unsplashChip'),
)
const modalDescription = computed(() =>
  isLibrarySource.value
    ? t('composer.picker.libraryDescription')
    : t('composer.picker.providerDescription'),
)

function toggleAsset(asset: ComposerMediaPickerAsset) {
  if (!asset.selectable || !isLibrarySource.value) return
  emit('toggle-asset', { assetId: asset.assetId })
}

function applySelection() {
  emit('apply-selection', { assetIds: selectedIds.value })
}

function submitProviderSearch() {
  emit('provider-search', { query: providerQuery.value.trim() })
}

function setSource(source: ComposerMediaPickerSource) {
  emit('set-active-source', { source })
}
</script>

<template>
  <div
    v-if="isOpen"
    class="fixed inset-0 z-[70] flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm"
    data-testid="composer-media-picker-shell"
  >
    <div class="flex max-h-[80vh] w-full max-w-5xl flex-col overflow-hidden rounded-[28px] border border-white/8 bg-[#2b2b2b] text-white shadow-2xl">
      <div class="flex items-start justify-between gap-6 border-b border-white/8 px-6 py-5">
        <div class="space-y-2">
          <h3 class="text-[24px] font-semibold leading-none text-white">
            {{ modalTitle }}
          </h3>
          <p class="text-sm text-white/65">
            {{ modalDescription }}
          </p>
        </div>
        <button
          type="button"
          class="flex size-9 items-center justify-center rounded-full border border-white/10 bg-white/5 text-white/70 transition hover:bg-white/10 hover:text-white"
          data-testid="picker-close"
          @click="emit('close')"
        >
          <X class="size-4" />
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-3 px-6 py-4">
        <button
          type="button"
          class="rounded-full border px-4 py-2 text-sm transition"
          :class="isLibrarySource ? 'border-[#8ccf70] bg-[#8ccf70]/15 text-white' : 'border-white/10 bg-white/5 text-white/70 hover:text-white'"
          data-testid="picker-source-library"
          @click="setSource('library')"
        >
          {{ t('composer.picker.libraryChip') }}
        </button>
        <button
          v-if="providerEnabled"
          type="button"
          class="rounded-full border px-4 py-2 text-sm transition"
          :class="!isLibrarySource ? 'border-[#8ccf70] bg-[#8ccf70]/15 text-white' : 'border-white/10 bg-white/5 text-white/70 hover:text-white'"
          data-testid="picker-source-unsplash"
          @click="setSource('unsplash')"
        >
          {{ t('composer.picker.unsplashChip') }}
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-6 pb-6">
        <form
          v-if="!isLibrarySource"
          data-testid="picker-provider-search"
          class="mb-5 flex items-center gap-3"
          @submit.prevent="submitProviderSearch"
        >
          <!-- biome-ignore lint/a11y/noLabelWithoutControl: for attribute correctly targets input#picker-provider-query below, but Biome cannot resolve it through the nested div wrapper -->
          <label class="sr-only" for="picker-provider-query">
            {{ t('composer.picker.providerSearchLabel') }}
          </label>
          <div class="flex flex-1 items-center gap-2 rounded-2xl border border-white/10 bg-[#202020] px-4 py-3">
            <Search class="size-4 text-white/45" />
            <input
              id="picker-provider-query"
              v-model="providerQuery"
              type="search"
              class="w-full bg-transparent text-sm text-white outline-none placeholder:text-white/35"
              :placeholder="t('composer.picker.searchPlaceholder')"
            >
          </div>
          <button type="submit" class="rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white transition hover:bg-white/10">
            {{ t('composer.picker.searchAction') }}
          </button>
        </form>

        <slot v-if="!isLibrarySource" name="provider" />

        <div v-if="isLibrarySource && collectionState === 'LOADING'" class="rounded-3xl border border-white/8 bg-black/10 px-5 py-8 text-sm text-white/65">
          {{ t('media.loading') }}
        </div>

        <div v-else-if="isLibrarySource && collectionState === 'EMPTY'" class="rounded-3xl border border-white/8 bg-black/10 px-5 py-8 text-sm text-white/65">
          <p class="font-medium text-white">{{ t('media.emptyTitle') }}</p>
          <p class="mt-2">{{ t('media.emptyBody') }}</p>
        </div>

        <div v-else-if="isLibrarySource && collectionState === 'ERROR'" class="rounded-3xl border border-error/40 bg-error/10 px-5 py-8 text-sm text-white">
          {{ t('composer.picker.errorLoad') }}
        </div>

        <div v-else-if="isLibrarySource" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <button
            v-for="asset in assets"
            :key="asset.assetId"
            type="button"
            class="group relative rounded-[24px] border p-3 text-left transition"
            :class="asset.selected ? 'border-[#8ccf70] bg-[#8ccf70]/10 shadow-[0_0_0_1px_rgba(140,207,112,0.18)]' : 'border-white/8 bg-[#242424] hover:border-white/15'"
            :data-testid="`picker-asset-card-${asset.assetId}`"
            :data-selected="String(asset.selected)"
            :aria-disabled="asset.selectable ? 'false' : 'true'"
            @click="toggleAsset(asset)"
          >
            <div
              v-if="asset.selected"
              class="absolute right-4 top-4 flex size-7 items-center justify-center rounded-full bg-[#8ccf70] text-[#1d1d1d]"
              :data-testid="`picker-asset-selected-indicator-${asset.assetId}`"
            >
              <Check class="size-4" />
            </div>
            <div v-if="asset.previewUrl" class="mb-4 overflow-hidden rounded-[18px] border border-white/8 bg-black/20">
              <img :src="asset.previewUrl" :alt="asset.name" class="h-40 w-full object-cover">
            </div>
            <div v-else class="mb-4 flex h-40 items-center justify-center rounded-[18px] border border-dashed border-white/12 bg-black/10 text-xs text-white/45">
              {{ t('composer.picker.noPreview') }}
            </div>
            <div class="space-y-1">
              <p class="text-base font-medium text-white">{{ asset.name }}</p>
              <p class="text-[11px] uppercase tracking-[0.28em] text-white/45">{{ asset.status }}</p>
            </div>
          </button>
        </div>
      </div>

      <div class="border-t border-white/8 px-6 py-5">
        <p
          v-if="applyDisabled && applyDisabledMessage"
          data-testid="picker-apply-warning"
          class="mb-4 rounded-2xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
        >
          {{ applyDisabledMessage }}
        </p>

        <div class="flex items-center justify-end gap-3">
          <button data-testid="picker-cancel" type="button" class="rounded-full border border-white/10 px-5 py-3 text-sm text-white/85 transition hover:bg-white/5" @click="emit('close')">
            {{ t('composer.picker.cancel') }}
          </button>
          <button data-testid="picker-apply" type="button" class="rounded-full bg-[#8ccf70] px-5 py-3 text-sm font-medium text-[#1b1b1b] transition disabled:cursor-not-allowed disabled:opacity-50" :disabled="applyDisabled || !isLibrarySource" @click="applySelection">
            {{ t('composer.picker.apply') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
