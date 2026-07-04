<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { XIcon } from '@lucide/vue'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type {
  ComposerMediaPickerProps,
  ComposerMediaPickerFilter,
  ComposerMediaPickerSearchChange,
  ComposerMediaPickerFilterChange,
} from './composer-media-picker.types'

const props = withDefaults(
  defineProps<ComposerMediaPickerProps>(),
  {
    disabled: false,
    errorMessage: null,
  },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'close'): void
  (e: 'search-change', payload: ComposerMediaPickerSearchChange): void
  (e: 'filter-change', payload: ComposerMediaPickerFilterChange): void
}>()

const { t } = useI18n()

function handleClose() {
  emit('close')
  emit('update:open', false)
}

function handleSearchInput(value: string | number) {
  if (props.disabled) return
  emit('search-change', { query: String(value) })
}

function handleFilterChange(event: Event) {
  if (props.disabled) return
  const target = event.target as HTMLSelectElement
  emit('filter-change', { filter: target.value as ComposerMediaPickerFilter })
}
</script>

<template>
  <Dialog :open="open" @update:open="(v) => emit('update:open', v)">
    <DialogContent class="sm:max-w-2xl" :show-close-button="false">
      <!-- biome-ignore lint/a11y/noStaticElementInteractions: captures Escape inside the nested dialog so it closes the picker without bubbling to the parent composer modal -->
      <div @keydown.escape.stop="handleClose">
        <DialogHeader>
        <div class="flex items-start justify-between gap-4">
          <div>
            <DialogTitle class="text-base font-medium text-text-display">
              {{ t('composer.mediaPicker.title') }}
            </DialogTitle>
            <DialogDescription class="mt-1 text-xs leading-5 text-text-secondary">
              {{ t('composer.mediaPicker.description') }}
            </DialogDescription>
          </div>
          <button
            type="button"
            data-testid="media-picker-close"
            class="rounded-full p-1.5 text-text-secondary transition-colors hover:bg-bg-primary hover:text-text-display focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
            :aria-label="t('composer.mediaPicker.close')"
            @click="handleClose"
          >
            <XIcon class="size-4" />
          </button>
        </div>
      </DialogHeader>

      <!-- Controls: search + filter -->
      <div class="flex items-center gap-3">
        <Input
          data-testid="media-picker-search"
          type="search"
          :model-value="searchQuery"
          :placeholder="t('composer.mediaPicker.searchPlaceholder')"
          :aria-label="t('composer.mediaPicker.searchLabel')"
          :disabled="disabled"
          class="flex-1"
          @update:model-value="handleSearchInput"
        />
        <select
          data-testid="media-picker-filter"
          :value="selectedFilter"
          :aria-label="t('composer.mediaPicker.filterLabel')"
          :disabled="disabled"
          class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
          @change="handleFilterChange"
        >
          <option
            v-for="opt in filterOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ t(opt.labelKey) }}
          </option>
        </select>
      </div>

      <!-- Disabled state -->
      <div v-if="disabled" class="py-8 text-center">
        <p class="text-sm font-medium text-text-display">
          {{ t('composer.mediaPicker.disabledTitle') }}
        </p>
        <p class="mt-1 text-xs text-text-secondary">
          {{ t('composer.mediaPicker.disabledBody') }}
        </p>
      </div>

      <!-- Loading state -->
      <div v-else-if="state === 'loading'" class="py-8 text-center">
        <p class="text-sm text-text-secondary">
          {{ t('composer.mediaPicker.loading') }}
        </p>
      </div>

      <!-- Empty state -->
      <div v-else-if="state === 'empty'" class="py-8 text-center">
        <p class="text-sm font-medium text-text-display">
          {{ t('composer.mediaPicker.emptyTitle') }}
        </p>
        <p class="mt-1 text-xs text-text-secondary">
          {{ t('composer.mediaPicker.emptyBody') }}
        </p>
      </div>

      <!-- Error state -->
      <div v-else-if="state === 'error'" class="py-8 text-center">
        <p class="text-sm font-medium text-error">
          {{ t('composer.mediaPicker.errorTitle') }}
        </p>
        <p class="mt-1 text-xs text-text-secondary">
          {{ errorMessage ?? t('composer.mediaPicker.errorBody') }}
        </p>
      </div>

      <!-- Ready state with asset grid -->
      <section
        v-else
        data-testid="media-picker-asset-grid"
        :aria-label="t('composer.mediaPicker.assetGridLabel')"
        class="grid grid-cols-3 gap-3 sm:grid-cols-4"
      >
        <div
          v-for="asset in assets"
          :key="asset.assetId"
          :data-testid="`media-picker-asset-${asset.assetId}`"
          class="flex flex-col items-center gap-1 rounded-xl border border-border-visible p-2"
        >
          <div class="flex size-16 items-center justify-center rounded-lg bg-bg-primary text-xs text-text-secondary">
            {{ asset.mediaType.split('/')[1]?.toUpperCase() ?? 'FILE' }}
          </div>
          <span class="line-clamp-1 max-w-full text-xs text-text-display">
            {{ asset.originalFilename ?? asset.assetId }}
          </span>
        </div>
      </section>
      </div>
    </DialogContent>
  </Dialog>
</template>
