<script setup lang="ts">
import { useSettingsStore } from '@/stores/settings'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const settings = useSettingsStore()
</script>

<template>
  <div class="space-y-12">
    <!-- Header -->
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('nav.settings') }}
      </h2>
      <p class="text-sm text-text-secondary">
        Configure language translations and dark/light modes.
      </p>
    </div>

    <Card class="bg-bg-surface border border-border-subtle max-w-xl">
      <CardHeader class="p-0 border-b border-border-subtle pb-4">
        <CardTitle class="label-mono text-text-display text-[10px]">
          Interface Preferences
        </CardTitle>
      </CardHeader>
      <CardContent class="p-0 mt-6 space-y-6">
        <!-- Locale preference -->
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium text-text-display block">Language / Idioma</span>
            <span class="text-xs text-text-secondary">Set the system default language.</span>
          </div>
          <div class="flex gap-2">
            <Button
              @click="settings.setLocale('en')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentLocale === 'en'
              }"
            >
              English
            </Button>
            <Button
              @click="settings.setLocale('es')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentLocale === 'es'
              }"
            >
              Español
            </Button>
          </div>
        </div>

        <div class="border-t border-border-visible my-4"></div>

        <!-- Theme preference -->
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium text-text-display block">Theme / Tema</span>
            <span class="text-xs text-text-secondary">Choose dark mode or light mode appearance.</span>
          </div>
          <div class="flex gap-2">
            <Button
              @click="settings.setTheme('dark')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentTheme === 'dark'
              }"
            >
              Dark
            </Button>
            <Button
              @click="settings.setTheme('light')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentTheme === 'light'
              }"
            >
              Light
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
