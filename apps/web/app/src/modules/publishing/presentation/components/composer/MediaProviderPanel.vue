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
  selectedForImport?: boolean
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
  // Guard: disabled when selectedForImport is true (parent owns the in-flight state).
  if (result.selectedForImport) return
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
    >
      {{ searchError }}
    </p>

    <div
      v-else-if="isSearching"
      class="rounded-2xl border border-dashed border-border-subtle px-4 py-6 text-sm text-text-secondary"
      data-testid="provider-panel-loading"
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
          <img :src="result.previewUrl" :alt="result.name" class="h-24 w-full object-cover">
        </div>
        <div v-else class="flex h-24 items-center justify-center rounded-xl border border-dashed border-border-visible bg-bg-primary/40 text-xs text-text-secondary">
          {{ t('composer.picker.noPreview') }}
        </div>
        <p class="text-sm font-medium text-text-display">{{ result.name }}</p>
        <p
          v-if="result.authorName"
          class="text-[11px] text-text-secondary"
        >
          {{ t('composer.picker.authorPrefix', { name: result.authorName }) }}
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="provider-panel-import"
          :data-provider-id="result.externalId"
          :disabled="result.selectedForImport"
          @click="importResult(result)"
        >
          {{ result.selectedForImport ? t('composer.picker.importingAction') : t('composer.picker.importAction') }}
        </Button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.provider-panel-shadow {
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.05);
}
</style>
