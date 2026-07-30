<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { locale, t } = useI18n()
const legalPath = (path: string) => locale.value === 'es' ? `/es${path}` : path
</script>

<template>
  <nav aria-label="Legal" class="flex flex-wrap justify-center gap-x-4 gap-y-2 text-sm text-text-secondary">
    <a :href="legalPath('/terms')" class="underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display">{{ t('auth.termsOfService') }}</a>
    <a :href="legalPath('/privacy')" class="underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display">{{ t('auth.privacyPolicy') }}</a>
  </nav>
</template>
