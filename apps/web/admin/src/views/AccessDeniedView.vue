<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <main class="dot-grid flex min-h-screen items-center justify-center bg-bg-primary px-4 py-10 text-text-body">
    <div class="admin-card max-w-md p-8 text-center">
      <h1 class="mb-2 text-2xl font-semibold text-text-display">{{ t('auth.accessDenied') }}</h1>
      <p class="mb-6 text-text-secondary">{{ t('auth.accessDeniedMessage') }}</p>
      <button
        class="admin-button-secondary"
        @click="router.push({ name: 'login' })"
      >
        {{ t('auth.signIn') }}
      </button>
    </div>
  </main>
</template>
