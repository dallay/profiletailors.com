<script setup lang="ts">
import darkOnLightLogoUrl from '@shared/assets/profiletailors-logotype.svg'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import AuthLegalLinks from './AuthLegalLinks.vue'
</script>

<template>
  <main class="auth-shell flex min-h-screen items-center justify-center bg-bg-primary px-4 py-10 text-text-body dot-grid">
    <section class="w-full max-w-md rounded-[28px] border border-border-subtle bg-bg-surface p-6 shadow-sm sm:p-8">
      <header class="mb-8 flex flex-col items-center gap-3 text-center">
        <img :src="darkOnLightLogoUrl" alt="" data-asset="profiletailors-logotype.svg" class="h-14 w-12 dark:hidden" aria-hidden="true">
        <img :src="lightOnDarkLogoUrl" alt="" data-asset="profiletailors-logotype-light.svg" class="hidden h-14 w-12 dark:block" aria-hidden="true">
        <span data-testid="brand-name" class="text-xl font-semibold tracking-tight text-text-display">Profile Tailors</span>
      </header>
      <slot />
      <AuthLegalLinks class="mt-8" />
    </section>
  </main>
</template>
