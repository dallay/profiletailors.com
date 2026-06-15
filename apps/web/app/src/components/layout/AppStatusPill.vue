<script setup lang="ts">
defineProps<{
  /** Verbatim summary text (e.g. "light mode / ES"). */
  summary: string
}>()
</script>

<template>
  <div
    class="rounded-full border border-border-visible bg-bg-surface px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary"
    role="status"
    aria-label="Session status"
  >
    {{ summary }}
  </div>
</template>
