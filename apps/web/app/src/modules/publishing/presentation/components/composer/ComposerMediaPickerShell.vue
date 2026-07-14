<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, Search, X } from '@lucide/vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog'
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
  (isOpen): void => {
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

function toggleAsset(asset: ComposerMediaPickerAsset): void {
  if (!asset.selectable || !isLibrarySource.value) return
  emit('toggle-asset', { assetId: asset.assetId })
}

function applySelection(): void {
  emit('apply-selection', { assetIds: selectedIds.value })
}

function submitProviderSearch(): void {
  emit('provider-search', { query: providerQuery.value.trim() })
}

function setSource(source: ComposerMediaPickerSource): void {
  emit('set-active-source', { source })
}

function onOpenChange(isOpen: boolean): void {
  if (!isOpen) emit('close')
}
</script>

<template>
  <Dialog :open="isOpen" @update:open="onOpenChange">
    <DialogContent
      class="z-[70] flex max-h-[80vh] w-[calc(100%-2rem)] max-w-5xl flex-col gap-0 overflow-hidden rounded-[28px] border border-border-subtle bg-bg-surface p-0 text-text-display shadow-2xl sm:max-w-5xl"
      data-testid="composer-media-picker-shell"
      :show-close-button="false"
    >
      <div class="flex items-start justify-between gap-6 border-b border-border-subtle px-6 py-5">
        <div class="space-y-2">
          <DialogTitle class="text-[24px] font-semibold leading-none text-text-display">
            {{ modalTitle }}
          </DialogTitle>
          <DialogDescription class="text-sm text-text-secondary">
            {{ modalDescription }}
          </DialogDescription>
        </div>
        <button
          type="button"
          class="flex size-9 items-center justify-center rounded-full border border-border-visible bg-bg-primary text-text-secondary transition hover:bg-bg-primary/80 hover:text-text-display"
          data-testid="picker-close"
          :aria-label="t('composer.picker.cancel')"
          @click="emit('close')"
        >
          <X class="size-4" />
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-3 px-6 py-4">
        <button
          type="button"
          class="rounded-full border px-4 py-2 text-sm transition"
          :class="isLibrarySource ? 'border-text-display bg-bg-primary text-text-display' : 'border-border-visible bg-bg-primary/50 text-text-secondary hover:text-text-display'"
          data-testid="picker-source-library"
          @click="setSource('library')"
        >
          {{ t('composer.picker.libraryChip') }}
        </button>
        <button
          v-if="providerEnabled"
          type="button"
          class="rounded-full border px-4 py-2 text-sm transition"
          :class="!isLibrarySource ? 'border-text-display bg-bg-primary text-text-display' : 'border-border-visible bg-bg-primary/50 text-text-secondary hover:text-text-display'"
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
          <!-- biome-ignore lint/a11y/noLabelWithoutControl: for attribute targets the nested input below -->
          <label class="sr-only" for="picker-provider-query">
            {{ t('composer.picker.providerSearchLabel') }}
          </label>
          <div class="flex flex-1 items-center gap-2 rounded-2xl border border-border-visible bg-bg-primary px-4 py-3">
            <Search class="size-4 text-text-secondary" />
            <input
              id="picker-provider-query"
              v-model="providerQuery"
              type="search"
              class="w-full bg-transparent text-sm text-text-display outline-none placeholder:text-text-secondary"
              :placeholder="t('composer.picker.searchPlaceholder')"
            >
          </div>
          <button type="submit" class="rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-display transition hover:bg-bg-primary/80">
            {{ t('composer.picker.searchAction') }}
          </button>
        </form>

        <slot v-if="!isLibrarySource" name="provider" />

        <div v-if="isLibrarySource && collectionState === 'LOADING'" class="rounded-3xl border border-border-subtle bg-bg-primary/30 px-5 py-8 text-sm text-text-secondary">
          {{ t('media.loading') }}
        </div>

        <div v-else-if="isLibrarySource && collectionState === 'EMPTY'" class="rounded-3xl border border-border-subtle bg-bg-primary/30 px-5 py-8 text-sm text-text-secondary">
          <p class="font-medium text-text-display">{{ t('media.emptyTitle') }}</p>
          <p class="mt-2">{{ t('media.emptyBody') }}</p>
        </div>

        <div v-else-if="isLibrarySource && collectionState === 'ERROR'" class="rounded-3xl border border-error/40 bg-error/10 px-5 py-8 text-sm text-error">
          {{ t('composer.picker.errorLoad') }}
        </div>

        <div v-else-if="isLibrarySource" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <button
            v-for="asset in assets"
            :key="asset.assetId"
            type="button"
            class="group relative rounded-[24px] border p-3 text-left transition"
            :class="asset.selected ? 'border-text-display bg-bg-primary shadow-[0_0_0_1px_var(--text-display)]' : 'border-border-subtle bg-bg-primary/50 hover:border-border-visible'"
            :data-testid="`picker-asset-card-${asset.assetId}`"
            :data-selected="String(asset.selected)"
            :aria-disabled="asset.selectable ? 'false' : 'true'"
            @click="toggleAsset(asset)"
          >
            <div
              v-if="asset.selected"
              class="absolute right-4 top-4 flex size-7 items-center justify-center rounded-full bg-text-display text-bg-primary"
              :data-testid="`picker-asset-selected-indicator-${asset.assetId}`"
            >
              <Check class="size-4" />
            </div>
            <div v-if="asset.previewUrl" class="mb-4 overflow-hidden rounded-[18px] border border-border-subtle bg-bg-primary/40">
              <img :src="asset.previewUrl" :alt="asset.name" class="h-40 w-full object-cover">
            </div>
            <div v-else class="mb-4 flex h-40 items-center justify-center rounded-[18px] border border-dashed border-border-visible bg-bg-primary/30 text-xs text-text-secondary">
              {{ t('composer.picker.noPreview') }}
            </div>
            <div class="space-y-1">
              <p class="text-base font-medium text-text-display">{{ asset.name }}</p>
              <p class="text-[11px] uppercase tracking-[0.28em] text-text-secondary">{{ asset.status }}</p>
            </div>
          </button>
        </div>
      </div>

      <div class="border-t border-border-subtle px-6 py-5">
        <p
          v-if="applyDisabled && applyDisabledMessage"
          data-testid="picker-apply-warning"
          class="mb-4 rounded-2xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
        >
          {{ applyDisabledMessage }}
        </p>

        <div class="flex items-center justify-end gap-3">
          <button data-testid="picker-cancel" type="button" class="rounded-full border border-border-visible px-5 py-3 text-sm text-text-secondary transition hover:bg-bg-primary hover:text-text-display" @click="emit('close')">
            {{ t('composer.picker.cancel') }}
          </button>
          <button data-testid="picker-apply" type="button" class="rounded-full bg-text-display px-5 py-3 text-sm font-medium text-bg-primary transition disabled:cursor-not-allowed disabled:opacity-50" :disabled="applyDisabled || !isLibrarySource" @click="applySelection">
            {{ t('composer.picker.apply') }}
          </button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
