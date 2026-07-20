<script setup lang="ts">
defineProps<{
  authorName?: string | null
  authorUrl?: string | null
  sourceProvider?: string | null
  licence?: string | null
}>()
</script>

<template>
  <div v-if="authorName || licence" class="space-y-0.5 px-3 pb-2">
    <p v-if="authorName" class="truncate text-[10px] text-text-secondary">
      {{ $t('media.attributionBy') }}
      <a
        v-if="authorUrl"
        :href="authorUrl"
        target="_blank"
        rel="noopener"
        class="underline underline-offset-2 hover:text-text-display"
      >{{ authorName }}</a>
      <template v-else>{{ authorName }}</template>
    </p>
    <p
      v-else-if="sourceProvider"
      class="truncate text-[10px] text-text-secondary"
    >
      {{ $t('media.viaProvider') }}
      <span class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ sourceProvider }}</span>
    </p>
    <p
      v-if="licence"
      class="font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary"
    >
      {{ licence }}
    </p>
  </div>
</template>
