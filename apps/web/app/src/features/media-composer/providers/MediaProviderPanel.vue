<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  searchProviderPhotos,
  importProviderPhoto,
  type MediaProviderSearchItem,
  type ProviderApiError,
} from '@/lib/media-providers'

const props = defineProps<{
  workspaceId: string
  token: string
}>()

const emit = defineEmits<(e: 'import-success', payload: { assetId: string; deduped: boolean }) => void>()

const { t } = useI18n()

type ViewState = 'idle' | 'loading' | 'ready' | 'error' | 'empty'

const view = ref<ViewState>('idle')
const items = ref<MediaProviderSearchItem[]>([])
const query = ref('')
const errorCode = ref<string | null>(null)
const importErrorCode = ref<string | null>(null)
const importingId = ref<string | null>(null)

async function runSearch() {
  if (!query.value.trim()) return
  view.value = 'loading'
  errorCode.value = null
  importErrorCode.value = null
  try {
    const response = await searchProviderPhotos(
      'unsplash',
      props.workspaceId,
      query.value.trim(),
      1,
      props.token,
    )
    items.value = response.items
    view.value = response.items.length === 0 ? 'empty' : 'ready'
  } catch (err) {
    errorCode.value = (err as ProviderApiError).code ?? null
    view.value = 'error'
  }
}

async function handleImport(item: MediaProviderSearchItem) {
  importingId.value = item.externalId
  importErrorCode.value = null
  try {
    const result = await importProviderPhoto(
      'unsplash',
      props.workspaceId,
      item.externalId,
      props.token,
    )
    emit('import-success', { assetId: result.assetId, deduped: result.deduped })
  } catch (err) {
    importErrorCode.value = (err as ProviderApiError).code ?? null
    if (items.value.length === 0) {
      view.value = 'error'
    }
  } finally {
    importingId.value = null
  }
}
</script>

<template>
  <div data-testid="media-provider-panel" class="flex flex-col gap-3">
    <form class="flex items-center gap-2" @submit.prevent="runSearch">
      <!-- biome-ignore lint/a11y/noLabelWithoutControl: the input is nested inside the label and the visible text is intentionally minimal -->
      <label class="flex-1">
        <span class="sr-only">{{ t('composer.mediaPicker.provider.searchLabel') }}</span>
        <Input
          data-testid="media-provider-search"
          type="search"
          :model-value="query"
          :placeholder="t('composer.mediaPicker.provider.searchPlaceholder')"
          :aria-label="t('composer.mediaPicker.provider.searchLabel')"
          @update:model-value="(v) => (query = String(v))"
        />
      </label>
      <Button
        type="submit"
        data-testid="media-provider-search-submit"
        variant="secondary"
      >
        {{ t('composer.mediaPicker.provider.searchAction') }}
      </Button>
    </form>

    <div aria-live="polite" aria-atomic="true">
      <p v-if="view === 'loading'" class="py-4 text-center text-sm text-text-secondary">
        {{ t('composer.mediaPicker.provider.loading') }}
      </p>

      <p v-else-if="view === 'empty'" class="py-4 text-center text-sm text-text-secondary">
        {{ t('composer.mediaPicker.provider.emptyTitle') }}
      </p>

      <p v-else-if="view === 'error'" class="py-4 text-center text-sm text-error">
        {{ t('composer.mediaPicker.provider.errorTitle') }}
      </p>

      <p v-else-if="view === 'idle'" class="py-4 text-center text-sm text-text-secondary">
        {{ t('composer.mediaPicker.provider.emptyTitle') }}
      </p>

      <p v-else-if="importErrorCode && view === 'ready'" class="py-4 text-center text-sm text-error">
        {{ t('composer.mediaPicker.provider.errorTitle') }}
      </p>
    </div>

    <div
      v-if="view === 'ready'"
      class="grid grid-cols-3 gap-3 sm:grid-cols-4"
      data-testid="media-provider-results"
    >
      <div
        v-for="item in items"
        :key="item.externalId"
        :data-testid="`media-provider-result-${item.externalId}`"
        class="flex flex-col items-center gap-1 rounded-xl border border-border-visible p-2"
      >
        <div class="flex size-16 items-center justify-center rounded-lg bg-bg-primary text-xs text-text-secondary">
          {{ item.width }}×{{ item.height }}
        </div>
        <Button
          type="button"
          :data-testid="`media-provider-import-${item.externalId}`"
          size="sm"
          :disabled="importingId === item.externalId"
          @click="handleImport(item)"
        >
          {{ importingId === item.externalId
              ? t('composer.mediaPicker.provider.importing')
              : t('composer.mediaPicker.provider.importAction') }}
        </Button>
      </div>
    </div>
  </div>
</template>
