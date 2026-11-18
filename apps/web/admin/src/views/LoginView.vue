<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t } = useI18n()
const route = useRoute()
const authStore = useAdminAuthStore()
function beginLogin(): void {
  const redirect = route.query.redirect
  authStore.signIn(typeof redirect === 'string' ? redirect : undefined)
}
</script>

<template>
  <main class="auth-shell dot-grid flex min-h-screen items-center justify-center bg-bg-primary px-4 py-10 text-text-body">
    <section class="w-full max-w-md rounded-[28px] border border-border-subtle bg-bg-surface p-6 sm:p-8">
      <header class="mb-8 flex flex-col items-center gap-3 text-center">
        <img :src="lightOnDarkLogoUrl" alt="" class="h-14 w-12" aria-hidden="true">
        <span class="text-xl font-semibold tracking-tight text-text-display">Profile Tailors</span>
        <p class="label-mono text-text-secondary">{{ t('auth.platformAdmin') }}</p>
      </header>

      <div class="space-y-5">
        <p class="text-center text-sm leading-6 text-text-secondary">{{ t('auth.adminSsoDescription') }}</p>
        <button type="button" class="min-h-11 w-full rounded-2xl bg-text-display px-4 font-semibold text-bg-primary" data-testid="admin-login-button" @click="beginLogin">
          {{ t('auth.signIn') }}
        </button>
      </div>
    </section>
  </main>
</template>
