<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { usePwaInstall } from './usePwaInstall'

const { t } = useI18n()
const { canInstall, install, showIosGuide } = usePwaInstall()
</script>

<template>
  <div v-if="canInstall || showIosGuide" class="px-2 pb-2 group-data-[collapsible=icon]:hidden">
    <button
      v-if="canInstall"
      type="button"
      class="w-full rounded-lg border border-border-visible px-3 py-2 text-sm text-text-display hover:bg-bg-surface"
      @click="install"
    >
      {{ t('pwa.install.cta') }}
    </button>
    <p v-if="showIosGuide" class="text-xs text-text-secondary">
      {{ t('pwa.install.iosGuide') }}
    </p>
  </div>
</template>
