<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ComposerMediaPickerAsset } from '@/components/composer/composer-media-picker.types'

/**
 * Provider-specific presentation layer for the composer media picker.
 *
 * This component does NOT fetch, persist, or reconcile media itself.
 * It renders results owned by the parent (CreatePostModal.vue) and emits
 * typed provider-search + provider-import interactions.
 *
 * The parent is responsible for:
 *   - invoking the provider API
 *   - reconciling returned persisted assets into the active picker session
 *   - keeping the picker open across imports for continued multi-selection
 *
 * Per the picker spec, this panel MUST NOT make HTTP calls. The search
 * input intentionally fires a typed event so the parent can decide
 * when to call the provider.
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
    /** Optional query the parent pre-filled (e.g., re-opening an earlier search). */
    initialQuery?: string
    /** Read-only results owned by the parent. */
    results?: ProviderSearchResultViewModel[]
    /** Whether the parent is currently fetching provider results. */
    isSearching?: boolean
    /** Provider-specific error message, if any. */
    searchError?: string | null
  }>(),
  {
    initialQuery: '',
    results: () => [] as ProviderSearchResultViewModel[],
    isSearching: false,
    searchError: null,
  },
)

const emit = defineEmits<{
  (e: 'provider-search', payload: { query: string }): void
  (e: 'provider-import', payload: { externalId: string }): void
}>()

const query = ref(props.initialQuery)
const debouncedImportStack = new Set<string>()

const sortedResults = computed(() =>
  [...props.results].sort((a, b) => a.name.localeCompare(b.name)),
)

function submitSearch() {
  emit('provider-search', { query: query.value.trim() })
}

function importResult(result: ProviderSearchResultViewModel) {
  // Guard against double-emit while the parent reconciles the import.
  if (debouncedImportStack.has(result.externalId)) return
  debouncedImportStack.add(result.externalId)
  emit('provider-import', { externalId: result.externalId })
  setTimeout(() => {
    debouncedImportStack.delete(result.externalId)
  }, 250)
}

defineExpose({
  /** Helper exposed for tests to confirm the panel stays open during reconciliation. */
  isOpen: () => true,
})
</script>

<template>
  <section
    class="flex flex-col gap-3"
    data-testid="provider-panel"
    aria-label="Provider search"
  >
    <form
      class="flex items-center gap-2"
      data-testid="picker-provider-search"
      @submit.prevent="submitSearch"
    >
      <label class="sr-only" for="provider-panel-query">Search Unsplash</label>
      <input
        id="provider-panel-query"
        v-model="query"
        type="search"
        placeholder="Search Unsplash"
        class="flex-1 rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm text-text-display"
      >
      <button
        type="submit"
        class="rounded-xl border border-border-visible px-3 py-2 text-xs"
        data-testid="provider-panel-search-submit"
        :disabled="isSearching"
      >
        {{ isSearching ? 'Searching…' : 'Search' }}
      </button>
    </form>

    <p
      v-if="searchError"
      class="rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
      data-testid="provider-panel-search-error"
    >
      {{ searchError }}
    </p>

    <div
      v-else-if="sortedResults.length === 0 && !isSearching"
      class="rounded-2xl border border-dashed border-border-subtle px-4 py-6 text-sm text-text-secondary"
      data-testid="provider-panel-empty"
    >
      Search to browse Unsplash photos. Imports keep this picker open for continued selection.
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
          No preview
        </div>
        <p class="text-sm font-medium text-text-display">{{ result.name }}</p>
        <p
          v-if="result.authorName"
          class="text-[11px] text-text-secondary"
        >
          by {{ result.authorName }}
        </p>
        <button
          type="button"
          class="rounded-xl border border-border-visible px-3 py-1.5 text-xs"
          data-testid="provider-panel-import"
          @click="importResult(result)"
        >
          {{ result.selectedForImport ? 'Importing…' : 'Import' }}
        </button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.provider-panel-shadow {
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.05);
}
</style>
