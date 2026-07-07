<script setup lang="ts">
import { ref, computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

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

const sortedResults = computed(() =>
  [...props.results].sort((a, b) => a.name.localeCompare(b.name)),
)

function submitSearch() {
  emit('provider-search', { query: query.value.trim() })
}

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
    aria-label="Provider search"
  >
    <form
      class="flex items-center gap-2"
      data-testid="picker-provider-search"
      @submit.prevent="submitSearch"
    >
      <label class="sr-only" for="provider-panel-query">Search Unsplash</label>
      <Input
        id="provider-panel-query"
        v-model="query"
        type="search"
        placeholder="Search Unsplash"
        class="flex-1"
      />
      <Button
        type="submit"
        variant="outline"
        size="sm"
        data-testid="provider-panel-search-submit"
        :disabled="isSearching"
      >
        {{ isSearching ? 'Searching…' : 'Search' }}
      </Button>
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
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="provider-panel-import"
          :disabled="result.selectedForImport"
          @click="importResult(result)"
        >
          {{ result.selectedForImport ? 'Importing…' : 'Import' }}
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
