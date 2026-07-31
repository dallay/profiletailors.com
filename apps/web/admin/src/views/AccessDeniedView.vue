<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-950">
    <div class="text-center p-8">
      <h1 class="text-2xl font-bold text-slate-100 mb-2">{{ t('auth.accessDenied') }}</h1>
      <p class="text-slate-400 mb-6">{{ t('auth.accessDeniedMessage') }}</p>
      <button
        class="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white transition-colors"
        @click="router.push({ name: 'login' })"
      >
        {{ t('auth.signIn') }}
      </button>
    </div>
  </div>
</template>
