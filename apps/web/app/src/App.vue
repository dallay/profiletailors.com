<script setup lang="ts">
import { PanelLeft } from '@lucide/vue'
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { useSettingsStore } from '@/stores/settings'

const route = useRoute()
const settings = useSettingsStore()

const isAuthRoute = computed(() => route.name === 'login' || route.name === 'register')

const currentSectionLabel = computed(() => {
  if (!route.name) {
    return 'dashboard'
  }

  return route.name === 'login' || route.name === 'register' ? 'login' : String(route.name)
})

const headerSummary = computed(() => {
  return currentSectionLabel.value === 'dashboard'
    ? 'Publishing control panel'
    : `${settings.currentTheme} mode / ${settings.currentLocale.toUpperCase()}`
})
</script>

<template>
  <RouterView v-if="isAuthRoute" />

  <TooltipProvider v-else>
    <SidebarProvider :default-open="true" class="bg-bg-primary font-sans text-text-body transition-colors duration-250">
      <AppSidebar />

      <SidebarInset>
        <div class="flex min-w-0 flex-1 flex-col">
          <header class="sticky top-0 z-20 border-b border-border-subtle bg-bg-primary/90 backdrop-blur">
            <div class="flex h-16 items-center justify-between gap-4 px-4 md:px-6 lg:px-8">
              <div class="flex min-w-0 items-center gap-3">
                <SidebarTrigger class="rounded-xl border border-border-visible bg-bg-surface text-text-display hover:bg-bg-primary">
                  <PanelLeft class="size-4" />
                  <span class="sr-only">Toggle navigation</span>
                </SidebarTrigger>

                <div class="min-w-0">
                  <p class="font-mono text-[10px] uppercase tracking-[0.18em] text-text-secondary">
                    Workspace
                  </p>
                  <h1 class="truncate text-sm font-medium text-text-display md:text-base">
                    {{ $t(`nav.${currentSectionLabel}`) || currentSectionLabel }}
                  </h1>
                </div>
              </div>

              <div class="hidden items-center gap-3 lg:flex">
                <div class="rounded-full border border-border-visible bg-bg-surface px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary">
                  {{ headerSummary }}
                </div>
              </div>

              <div class="flex items-center gap-2 md:gap-3">
                <div class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]">
                  <button
                    class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                    :class="settings.currentLocale === 'en' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                    @click="settings.setLocale('en')"
                  >
                    EN
                  </button>
                  <button
                    class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                    :class="settings.currentLocale === 'es' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                    @click="settings.setLocale('es')"
                  >
                    ES
                  </button>
                </div>

                <div class="hidden items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px] md:flex">
                  <button
                    class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                    :class="settings.currentTheme === 'dark' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                    @click="settings.setTheme('dark')"
                  >
                    dark
                  </button>
                  <button
                    class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                    :class="settings.currentTheme === 'light' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                    @click="settings.setTheme('light')"
                  >
                    light
                  </button>
                </div>
              </div>
            </div>
          </header>

          <main class="dot-grid flex-1 overflow-y-auto px-4 py-6 md:px-6 lg:px-8 lg:py-8">
            <div class="mx-auto w-full max-w-7xl">
              <RouterView />
            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  </TooltipProvider>
</template>
