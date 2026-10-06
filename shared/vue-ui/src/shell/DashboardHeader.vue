<script setup lang="ts">
import { SidebarTrigger } from './sidebar'

defineProps<{
  eyebrow: string
  title: string
  toggleLabel: string
  titleDataTour?: string
}>()
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-border-subtle bg-bg-primary">
    <div class="flex h-16 items-center justify-between gap-4 px-4 md:px-6 lg:px-8">
      <div class="flex min-w-0 items-center gap-3">
        <SidebarTrigger
          class="size-9 rounded-lg border border-border-visible bg-bg-surface text-text-display hover:bg-bg-primary"
          :aria-label="toggleLabel"
        />
        <div class="min-w-0" :data-tour="titleDataTour">
          <p class="font-mono text-[10px] uppercase tracking-[0.18em] text-text-secondary">
            {{ eyebrow }}
          </p>
          <h1 class="truncate text-sm font-medium text-text-display md:text-base">
            {{ title }}
          </h1>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>
