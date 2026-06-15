<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PanelLeft } from '@lucide/vue'
import { useSettingsStore } from '@/stores/settings'
import AppStatusPill from './AppStatusPill.vue'
import AppLanguagePill from './AppLanguagePill.vue'

const route = useRoute()
const { t } = useI18n()
const settings = useSettingsStore()

const emit = defineEmits<(e: 'setLocale', locale: 'en' | 'es') => void>()

const currentSectionLabel = computed(() => {
  if (!route.name) return 'dashboard'
  return String(route.name)
})

const headerSummary = computed(() => {
  return currentSectionLabel.value === 'dashboard'
    ? 'Publishing control panel'
    : `${settings.currentTheme} mode / ${settings.currentLocale.toUpperCase()}`
})

function onLanguageChange(locale: 'en' | 'es') {
  emit('setLocale', locale)
}
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-border-subtle bg-bg-primary/90 backdrop-blur">
    <div class="flex h-16 items-center justify-between gap-4 px-4 md:px-6 lg:px-8">
      <div class="flex min-w-0 items-center gap-3">
        <button
          type="button"
          class="rounded-xl border border-border-visible bg-bg-surface text-text-display hover:bg-bg-primary"
          aria-label="Toggle navigation"
        >
          <PanelLeft class="size-4" />
          <span class="sr-only">Toggle navigation</span>
        </button>

        <div class="min-w-0">
          <p class="font-mono text-[10px] uppercase tracking-[0.18em] text-text-secondary">
            Workspace
          </p>
          <h1 class="truncate text-sm font-medium text-text-display md:text-base">
            {{ t(`nav.${currentSectionLabel}`) || currentSectionLabel }}
          </h1>
        </div>
      </div>

      <div class="hidden items-center gap-3 lg:flex">
        <AppStatusPill :summary="headerSummary" />
      </div>

      <div class="flex items-center gap-2 md:gap-3">
        <AppLanguagePill
          :current="settings.currentLocale"
          @change="onLanguageChange"
        />
      </div>
    </div>
  </header>
</template>
