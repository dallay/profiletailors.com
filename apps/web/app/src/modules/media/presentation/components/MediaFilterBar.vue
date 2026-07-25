<script setup lang="ts">
type StatusFilter = 'ALL' | 'READY' | 'PROCESSING' | 'FAILED' | 'SUSPENDED'
type TypeFilter = 'ALL' | 'IMAGE' | 'VIDEO' | 'PDF' | 'OTHER'
type SortBy = 'newest' | 'oldest' | 'filename-asc' | 'filename-desc' | 'size-desc' | 'size-asc' | 'status'

const props = defineProps<{
  searchQuery: string
  statusFilter: StatusFilter
  typeFilter: TypeFilter
  sortBy: SortBy
  allVisibleSelected: boolean
  visibleCount: number
  totalCount: number
  readyCount: number
  processingCount: number
  failedCount: number
}>()

const emit = defineEmits<{
  (e: 'update:searchQuery', val: string): void
  (e: 'update:statusFilter', val: StatusFilter): void
  (e: 'update:typeFilter', val: TypeFilter): void
  (e: 'update:sortBy', val: SortBy): void
  (e: 'toggle-select-all'): void
}>()
</script>

<template>
  <div class="space-y-4">
    <!-- Stats badges -->
    <div class="flex flex-wrap items-center gap-3">
      <div class="inline-flex items-center gap-2 rounded-full border border-success/30 bg-success/10 px-4 py-2">
        <span class="size-2 rounded-full bg-success" />
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-display">{{ $t('media.readyTitle') }}</span>
        <span class="font-mono text-sm font-medium text-text-display">{{ readyCount }}</span>
      </div>
      <div class="inline-flex items-center gap-2 rounded-full border border-text-display/20 bg-text-display/5 px-4 py-2">
        <span class="size-2 rounded-full bg-text-secondary" />
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-display">{{ $t('media.processingTitle') }}</span>
        <span class="font-mono text-sm font-medium text-text-display">{{ processingCount }}</span>
      </div>
      <div class="inline-flex items-center gap-2 rounded-full border border-error/30 bg-error/10 px-4 py-2">
        <span class="size-2 rounded-full bg-error" />
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-display">{{ $t('media.failedTitle') }}</span>
        <span class="font-mono text-sm font-medium text-text-display">{{ failedCount }}</span>
      </div>
    </div>

    <!-- Filter controls row -->
    <div class="flex flex-wrap items-center gap-3 rounded-2xl border border-border-subtle bg-bg-primary/30 px-5 py-4">
      <div class="flex items-center gap-2">
        <input
          id="select-all-visible"
          :checked="allVisibleSelected"
          type="checkbox"
          class="size-4"
          @change="emit('toggle-select-all')"
        />
        <label for="select-all-visible" class="sr-only">{{ $t('media.selectAllVisible') }}</label>
      </div>

      <div class="min-w-0 flex-1 lg:max-w-xs">
        <input
          :value="searchQuery"
          :aria-label="$t('media.searchLabel')"
          type="search"
          class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
          :placeholder="$t('media.searchPlaceholder')"
          @input="emit('update:searchQuery', ($event.target as HTMLInputElement).value)"
        />
      </div>

      <select
        :value="statusFilter"
        data-testid="filter-status"
        :aria-label="$t('media.statusFilter')"
        class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
        @change="emit('update:statusFilter', ($event.target as HTMLSelectElement).value as StatusFilter)"
      >
        <option value="ALL">{{ $t('media.filterAll') }}</option>
        <option value="READY">READY</option>
        <option value="PROCESSING">PROCESSING</option>
        <option value="FAILED">FAILED</option>
        <option value="SUSPENDED">SUSPENDED</option>
      </select>

      <select
        :value="typeFilter"
        data-testid="filter-type"
        :aria-label="$t('media.typeFilter')"
        class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
        @change="emit('update:typeFilter', ($event.target as HTMLSelectElement).value as TypeFilter)"
      >
        <option value="ALL">{{ $t('media.filterAll') }}</option>
        <option value="IMAGE">{{ $t('media.typeImage') }}</option>
        <option value="VIDEO">{{ $t('media.typeVideo') }}</option>
        <option value="PDF">{{ $t('media.typePdf') }}</option>
        <option value="OTHER">{{ $t('media.typeOther') }}</option>
      </select>

      <select
        :value="sortBy"
        data-testid="filter-sort"
        :aria-label="$t('media.sortLabel')"
        class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
        @change="emit('update:sortBy', ($event.target as HTMLSelectElement).value as SortBy)"
      >
        <option value="newest">{{ $t('media.sortNewest') }}</option>
        <option value="oldest">{{ $t('media.sortOldest') }}</option>
        <option value="filename-asc">{{ $t('media.sortFilenameAsc') }}</option>
        <option value="filename-desc">{{ $t('media.sortFilenameDesc') }}</option>
        <option value="size-desc">{{ $t('media.sortSizeDesc') }}</option>
        <option value="size-asc">{{ $t('media.sortSizeAsc') }}</option>
        <option value="status">{{ $t('media.sortStatus') }}</option>
      </select>

      <span class="text-xs text-text-secondary">{{ visibleCount }} / {{ totalCount }}</span>
    </div>
  </div>
</template>
