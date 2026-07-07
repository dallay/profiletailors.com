<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerApplyPayload,
  ComposerMediaPickerCollectionState,
  ComposerMediaPickerProviderSearchPayload,
  ComposerMediaPickerTogglePayload,
} from './composer-media-picker.types'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
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
}>()

const { t } = useI18n()
const providerQuery = ref('')

const selectedIds = computed(() => props.assets.filter((asset) => asset.selected).map((asset) => asset.assetId))

function toggleAsset(asset: ComposerMediaPickerAsset) {
  if (!asset.selectable) return
  emit('toggle-asset', { assetId: asset.assetId })
}

function applySelection() {
  emit('apply-selection', { assetIds: selectedIds.value })
}

function submitProviderSearch() {
  emit('provider-search', { query: providerQuery.value.trim() })
}
</script>

<template>
  <div v-if="isOpen" class="flex flex-col gap-4" data-testid="composer-media-picker-shell">
    <div class="flex items-center justify-between">
      <h3 class="font-mono text-xs font-bold uppercase tracking-widest text-text-display">
        {{ t('composer.picker.header') }}
      </h3>
      <div class="flex items-center gap-2 text-xs text-text-secondary">
        <span class="rounded-full border border-border-subtle px-3 py-1">{{ t('composer.picker.libraryChip') }}</span>
        <span v-if="provider === 'unsplash'" class="rounded-full border border-border-subtle px-3 py-1">{{ t('composer.picker.unsplashChip') }}</span>
      </div>
    </div>

    <form
      v-if="provider === 'unsplash'"
      data-testid="picker-provider-search"
      class="flex items-center gap-2"
      @submit.prevent="submitProviderSearch"
    >
      <!-- biome-ignore lint/a11y/noLabelWithoutControl: t() provides accessible text, Biome can't resolve i18n keys statically -->
      <!-- biome-ignore lint/a11y/noLabelWithoutControl: label is associated via `for` to the search input below -->
      <label class="sr-only" for="picker-provider-query">
        {{ t('composer.picker.searchPlaceholder') }}
      </label>
      <input
        id="picker-provider-query"
        v-model="providerQuery"
        type="search"
        class="flex-1 rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm text-text-display"
        :placeholder="t('composer.picker.searchPlaceholder')"
      >
      <button type="submit" class="rounded-xl border border-border-visible px-3 py-2 text-xs">{{ t('composer.picker.searchAction') }}</button>
    </form>

    <slot
      v-if="provider === 'unsplash'"
      name="provider"
    />

    <div v-if="collectionState === 'LOADING'" class="rounded-2xl border border-border-subtle bg-bg-primary/30 px-4 py-6 text-sm text-text-secondary">
      {{ t('media.loading') }}
    </div>

    <div v-else-if="collectionState === 'EMPTY'" class="rounded-2xl border border-border-subtle bg-bg-primary/30 px-4 py-6 text-sm text-text-secondary">
      <p class="font-medium text-text-display">{{ t('media.emptyTitle') }}</p>
      <p class="mt-1">{{ t('media.emptyBody') }}</p>
    </div>

    <div v-else-if="collectionState === 'ERROR'" class="rounded-2xl border border-error/40 bg-error/10 px-4 py-6 text-sm text-text-body">
      {{ t('composer.picker.errorLoad') }}
    </div>

    <div v-else class="grid gap-3 sm:grid-cols-2">
      <button
        v-for="asset in assets"
        :key="asset.assetId"
        type="button"
        class="rounded-2xl border p-3 text-left"
        :data-testid="`picker-asset-card-${asset.assetId}`"
        :data-selected="String(asset.selected)"
        :aria-disabled="asset.selectable ? 'false' : 'true'"
        @click="toggleAsset(asset)"
      >
        <div v-if="asset.previewUrl" class="mb-3 overflow-hidden rounded-xl border border-border-subtle bg-bg-primary/40">
          <img :src="asset.previewUrl" :alt="asset.name" class="h-24 w-full object-cover">
        </div>
        <div v-else class="mb-3 flex h-24 items-center justify-center rounded-xl border border-dashed border-border-visible bg-bg-primary/40 text-xs text-text-secondary">
          {{ t('composer.picker.noPreview') }}
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium text-text-display">{{ asset.name }}</p>
          <p class="text-[11px] uppercase tracking-wider text-text-secondary">{{ asset.status }}</p>
        </div>
      </button>
    </div>

    <p
      v-if="applyDisabled && applyDisabledMessage"
      data-testid="picker-apply-warning"
      class="rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
    >
      {{ applyDisabledMessage }}
    </p>

    <div class="flex items-center justify-end gap-2">
      <button data-testid="picker-cancel" type="button" class="rounded-full border border-border-visible px-4 py-2 text-xs" @click="emit('close')">
        {{ t('composer.picker.cancel') }}
      </button>
      <button data-testid="picker-apply" type="button" class="rounded-full bg-text-display px-4 py-2 text-xs text-bg-primary disabled:cursor-not-allowed disabled:opacity-50" :disabled="applyDisabled" @click="applySelection">
        {{ t('composer.picker.apply') }}
      </button>
    </div>
  </div>
</template>
