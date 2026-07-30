<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>

<template>
  <section class="space-y-5 text-center">
    <h1 class="text-2xl font-semibold text-text-display">{{ t('passwordRecovery.unavailableTitle') }}</h1>
    <p class="text-sm leading-6 text-text-secondary">{{ t('passwordRecovery.unavailableMessage') }}</p>
    <RouterLink :to="{ name: 'login' }" class="inline-flex min-h-11 items-center justify-center rounded-2xl border border-border-visible px-5 font-medium">{{ t('passwordRecovery.backToLogin') }}</RouterLink>
  </section>
</template>
