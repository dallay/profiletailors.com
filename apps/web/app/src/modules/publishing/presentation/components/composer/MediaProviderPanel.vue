<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'

/**
 * Provider-specific presentation layer for the composer media picker.
 * The parent owns fetch/import orchestration and supplies the results.
 */

export interface ProviderSearchResultViewModel {
  externalId: string
  previewUrl: string | null
  name: string
  authorName?: string | null
  authorUrl?: string | null
  sourceUrl?: string | null
  selectedForImport?: boolean
  imported?: boolean
}

const props = withDefaults(
  defineProps<{
    /** Read-only results owned by the parent. */
    results?: ProviderSearchResultViewModel[]
    /** Whether the parent is currently fetching provider results. */
    isSearching?: boolean
    /** Provider-specific error message, if any. */
    searchError?: string | null
  }>(),
  {
    results: () => [] as ProviderSearchResultViewModel[],
    isSearching: false,
    searchError: null,
  },
)

const emit = defineEmits<(e: 'provider-import', payload: { externalId: string }) => void>()

const { t } = useI18n()

const sortedResults = computed(() =>
  [...props.results].sort((a, b) => a.name.localeCompare(b.name)),
)

function importResult(result: ProviderSearchResultViewModel) {
  if (result.selectedForImport || result.imported) return
  emit('provider-import', { externalId: result.externalId })
}

// No internal open/close state — panel is rendered when in the shell's DOM and
// dismissed by the parent (ComposerMediaPickerShell) closing the whole picker.
</script>

<template>
  <section
    class="flex flex-col gap-3"
    data-testid="provider-panel"
:aria-label="t('composer.picker.providerSearchLabel')"
  >
    <p
      v-if="searchError"
      class="rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
      data-testid="provider-panel-search-error"
      role="alert"
      aria-live="polite"
    >
      {{ searchError }}
    </p>

    <div
      v-if="isSearching"
      class="rounded-2xl border border-dashed border-border-subtle px-4 py-6 text-sm text-text-secondary"
      data-testid="provider-panel-loading"
      role="status"
      aria-live="polite"
    >
      {{ t('composer.picker.searchingAction') }}
    </div>

    <div
      v-else-if="sortedResults.length === 0"
      class="rounded-2xl border border-dashed border-border-subtle px-4 py-6 text-sm text-text-secondary"
      data-testid="provider-panel-empty"
    >
      {{ t('composer.picker.providerEmpty') }}
    </div>

    <div
      v-else
      class="grid gap-3 sm:grid-cols-2"
      data-testid="provider-panel-results"
    >
      <article
        v-for="result in sortedResults"
        :key="result.externalId"
        class="flex flex-col gap-2 rounded-2xl border border-border-subtle bg-bg-primary/30 p-3 text-left"
        :data-testid="`provider-result-${result.externalId}`"
      >
        <div v-if="result.previewUrl" class="overflow-hidden rounded-xl border border-border-subtle bg-bg-primary/40">
          <img
            :src="result.previewUrl"
            :alt="result.name"
            class="h-40 w-full object-cover"
            loading="lazy"
            decoding="async"
          >
        </div>
        <div v-else class="flex h-24 items-center justify-center rounded-xl border border-dashed border-border-visible bg-bg-primary/40 text-xs text-text-secondary">
          {{ t('composer.picker.noPreview') }}
        </div>
        <p class="text-sm font-medium text-text-display">{{ result.name }}</p>
        <p v-if="result.authorName" class="text-[11px] text-text-secondary">
          {{ t('composer.picker.photoBy') }}
          <!-- biome-ignore lint/a11y/useValidAnchor: authorUrl is guaranteed by the surrounding conditional -->
          <a
            v-if="result.authorUrl"
            :href="result.authorUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="underline underline-offset-2 hover:text-text-display"
          >{{ result.authorName }}</a>
          <span v-else>{{ result.authorName }}</span>
          {{ t('composer.picker.onProvider') }}
          <!-- biome-ignore lint/a11y/useValidAnchor: sourceUrl is guaranteed by the surrounding conditional -->
          <a
            v-if="result.sourceUrl"
            :href="result.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="underline underline-offset-2 hover:text-text-display"
          >Unsplash</a>
          <span v-else>Unsplash</span>
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="provider-panel-import"
          :data-provider-id="result.externalId"
          :disabled="result.selectedForImport || result.imported"
          @click="importResult(result)"
        >
          {{ result.imported ? t('composer.picker.importedAction') : result.selectedForImport ? t('composer.picker.importingAction') : t('composer.picker.importAction') }}
        </Button>
      </article>
    </div>
  </section>
</template>

