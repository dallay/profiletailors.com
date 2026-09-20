<script setup lang="ts">
const props = defineProps<{
  page: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
}>()

const emit = defineEmits<{
  previous: []
  next: []
}>()
</script>

<template>
  <nav class="mt-4 flex items-center justify-between gap-3" aria-label="Pagination">
    <button
      type="button"
      class="rounded-full border border-border-visible px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.06em] text-text-body disabled:opacity-40"
      :disabled="!props.hasPrevious"
      @click="emit('previous')"
    >
      Previous
    </button>
    <span class="font-mono text-xs text-text-secondary">
      {{ props.page + 1 }} / {{ props.totalPages }}
    </span>
    <button
      type="button"
      class="rounded-full border border-border-visible px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.06em] text-text-body disabled:opacity-40"
      :disabled="!props.hasNext"
      @click="emit('next')"
    >
      Next
    </button>
  </nav>
</template>
