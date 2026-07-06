<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useId } from 'vue'
import { XIcon } from '@lucide/vue'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
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
    provider: null,
  },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'close'): void
  (e: 'search-change', payload: ComposerMediaPickerSearchChange): void
  (e: 'filter-change', payload: ComposerMediaPickerFilterChange): void
  (e: 'provider-import', payload: { externalId: string }): void
}>()

const { t } = useI18n()
const searchLabelId = useId()
const filterLabelId = useId()

function handleClose() {
  emit('close')
  emit('update:open', false)
}

function handleSearchInput(value: string | number) {
  if (props.disabled) return
  emit('search-change', { query: String(value) })
}

function handleFilterChange(value: unknown) {
  if (props.disabled || typeof value !== 'string') return
  emit('filter-change', { filter: value as ComposerMediaPickerFilter })
}

defineSlots<{
  providerTab(): unknown
}>()
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

<!-- Body: either a single asset tab or asset + provider tabs -->
      <template v-if="provider">
        <Tabs default-value="library" class="w-full">
          <TabsList class="grid w-full grid-cols-2">
            <TabsTrigger value="library" data-testid="media-picker-library-tab-trigger">
              {{ t('composer.mediaPicker.tabs.library') }}
            </TabsTrigger>
            <TabsTrigger value="provider" data-testid="media-picker-provider-tab-trigger">
              {{ t('composer.mediaPicker.tabs.provider') }}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="library" class="flex flex-col gap-3">
            <div class="flex items-center gap-3">
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: dynamic :for resolved at runtime to the nested Input id -->
              <label :for="searchLabelId" class="flex-1">
                <span class="sr-only">{{ t('composer.mediaPicker.searchLabel') }}</span>
                <Input
                  :id="searchLabelId"
                  data-testid="media-picker-search"
                  type="search"
                  :model-value="searchQuery"
                  :placeholder="t('composer.mediaPicker.searchPlaceholder')"
                  :aria-label="t('composer.mediaPicker.searchLabel')"
                  :disabled="disabled"
                  @update:model-value="handleSearchInput"
                />
              </label>
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: dynamic :for resolved at runtime to the nested SelectTrigger id -->
              <label :for="filterLabelId" class="block">
                <span class="sr-only">{{ t('composer.mediaPicker.filterLabel') }}</span>
                <Select
                  :model-value="selectedFilter"
                  :disabled="disabled"
                  @update:model-value="handleFilterChange"
                >
                  <SelectTrigger
                    :id="filterLabelId"
                    data-testid="media-picker-filter"
                    :aria-label="t('composer.mediaPicker.filterLabel')"
                    :disabled="disabled"
                    class="w-40"
                  >
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="opt in filterOptions" :key="opt.value" :value="opt.value">
                      {{ t(opt.labelKey) }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </label>
            </div>

            <div aria-live="polite" aria-atomic="true">
              <div v-if="disabled" class="py-8 text-center">
                <p class="text-sm font-medium text-text-display">{{ t('composer.mediaPicker.disabledTitle') }}</p>
                <p class="mt-1 text-xs text-text-secondary">{{ t('composer.mediaPicker.disabledBody') }}</p>
              </div>
              <div v-else-if="state === 'loading'" class="py-8 text-center">
                <p class="text-sm text-text-secondary">{{ t('composer.mediaPicker.loading') }}</p>
              </div>
              <div v-else-if="state === 'empty'" class="py-8 text-center">
                <p class="text-sm font-medium text-text-display">{{ t('composer.mediaPicker.emptyTitle') }}</p>
                <p class="mt-1 text-xs text-text-secondary">{{ t('composer.mediaPicker.emptyBody') }}</p>
              </div>
              <div v-else-if="state === 'error'" class="py-8 text-center">
                <p class="text-sm font-medium text-error">{{ t('composer.mediaPicker.errorTitle') }}</p>
                <p class="mt-1 text-xs text-text-secondary">{{ errorMessage ?? t('composer.mediaPicker.errorBody') }}</p>
              </div>
            </div>

            <section
              v-if="!disabled && state === 'ready'"
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
          </TabsContent>
          <TabsContent
            value="provider"
            class="flex flex-col gap-3"
            data-testid="media-picker-provider-tab-content"
          >
            <slot name="providerTab" />
          </TabsContent>
        </Tabs>
      </template>
      <template v-else>
        <!-- Controls: search + filter -->
        <div class="flex items-center gap-3">
          <!-- biome-ignore lint/a11y/noLabelWithoutControl: dynamic :for resolved at runtime to the nested Input id -->
          <label :for="searchLabelId" class="flex-1">
            <span class="sr-only">{{ t('composer.mediaPicker.searchLabel') }}</span>
            <Input
              :id="searchLabelId"
              data-testid="media-picker-search"
              type="search"
              :model-value="searchQuery"
              :placeholder="t('composer.mediaPicker.searchPlaceholder')"
              :aria-label="t('composer.mediaPicker.searchLabel')"
              :disabled="disabled"
              @update:model-value="handleSearchInput"
            />
          </label>
          <!-- biome-ignore lint/a11y/noLabelWithoutControl: dynamic :for resolved at runtime to the nested SelectTrigger id -->
          <label :for="filterLabelId" class="block">
            <span class="sr-only">{{ t('composer.mediaPicker.filterLabel') }}</span>
            <Select
              :model-value="selectedFilter"
              :disabled="disabled"
              @update:model-value="handleFilterChange"
            >
              <SelectTrigger
                :id="filterLabelId"
                data-testid="media-picker-filter"
                :aria-label="t('composer.mediaPicker.filterLabel')"
                :disabled="disabled"
                class="w-40"
              >
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="opt in filterOptions" :key="opt.value" :value="opt.value">
                  {{ t(opt.labelKey) }}
                </SelectItem>
              </SelectContent>
            </Select>
          </label>
        </div>

        <div aria-live="polite" aria-atomic="true">
          <div v-if="disabled" class="py-8 text-center">
            <p class="text-sm font-medium text-text-display">{{ t('composer.mediaPicker.disabledTitle') }}</p>
            <p class="mt-1 text-xs text-text-secondary">{{ t('composer.mediaPicker.disabledBody') }}</p>
          </div>
          <div v-else-if="state === 'loading'" class="py-8 text-center">
            <p class="text-sm text-text-secondary">{{ t('composer.mediaPicker.loading') }}</p>
          </div>
          <div v-else-if="state === 'empty'" class="py-8 text-center">
            <p class="text-sm font-medium text-text-display">{{ t('composer.mediaPicker.emptyTitle') }}</p>
            <p class="mt-1 text-xs text-text-secondary">{{ t('composer.mediaPicker.emptyBody') }}</p>
          </div>
          <div v-else-if="state === 'error'" class="py-8 text-center">
            <p class="text-sm font-medium text-error">{{ t('composer.mediaPicker.errorTitle') }}</p>
            <p class="mt-1 text-xs text-text-secondary">{{ errorMessage ?? t('composer.mediaPicker.errorBody') }}</p>
          </div>
        </div>

        <section
          v-if="!disabled && state === 'ready'"
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
      </template>
      </div>
    </DialogContent>
  </Dialog>
</template>
