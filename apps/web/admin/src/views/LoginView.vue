<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-950">
    <div class="w-full max-w-sm p-8 rounded-xl bg-slate-900 border border-slate-800">
      <h1 class="text-lg font-semibold text-slate-100 mb-1">{{ t('auth.platformAdmin') }}</h1>
      <p class="text-sm text-slate-400 mb-6">Profile Tailors</p>
      <a
        href="/api/auth/login"
        class="block w-full text-center py-2 px-4 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-semibold transition-colors"
      >
        {{ t('auth.signIn') }}
      </a>
    </div>
  </div>
</template>
