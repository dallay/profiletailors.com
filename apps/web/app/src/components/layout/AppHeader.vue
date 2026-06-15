<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SidebarTrigger } from '@/components/ui/sidebar'

const route = useRoute()
const { t } = useI18n()

const currentSectionLabel = computed(() => {
  if (!route.name) return 'dashboard'
  return String(route.name)
})
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-border-subtle bg-bg-primary/90 backdrop-blur">
    <div class="flex h-16 items-center justify-between gap-4 px-4 md:px-6 lg:px-8">
      <div class="flex min-w-0 items-center gap-3">
        <SidebarTrigger class="rounded-xl border border-border-visible bg-bg-surface text-text-display hover:bg-bg-primary size-9" />

        <div class="min-w-0">
          <p class="font-mono text-[10px] uppercase tracking-[0.18em] text-text-secondary">
            Workspace
          </p>
          <h1 class="truncate text-sm font-medium text-text-display md:text-base">
            {{ t(`nav.${currentSectionLabel}`) || currentSectionLabel }}
          </h1>
        </div>
      </div>
    </div>
  </header>
</template>
