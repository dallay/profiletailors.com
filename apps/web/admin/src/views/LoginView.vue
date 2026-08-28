<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { ApiRequestError } from '@/lib/api'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAdminAuthStore()
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const pending = ref(false)
const errors = ref<{ email?: string; password?: string }>({})
const formError = ref<string | null>(null)
const emailInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)
const errorAlert = ref<HTMLElement | null>(null)

async function submit(): Promise<void> {
  if (pending.value) return

  errors.value = {}
  formError.value = null
  const normalizedEmail = email.value.trim().toLowerCase()
  const normalizedPassword = password.value.trim()

  if (!normalizedEmail) errors.value.email = 'emailRequired'
  else if (!/^\S+@\S+\.\S+$/.test(normalizedEmail)) errors.value.email = 'emailInvalid'
  if (!normalizedPassword) errors.value.password = 'passwordRequired'

  if (Object.keys(errors.value).length > 0) {
    await nextTick()
    ;(errors.value.email ? emailInput.value : passwordInput.value)?.focus()
    return
  }

  pending.value = true
  try {
    await authStore.signIn(normalizedEmail, normalizedPassword)
    await router.replace(resolveRedirect(route.query.redirect))
  } catch (error) {
    formError.value = error instanceof ApiRequestError && error.status === 403
      ? 'accessDeniedMessage'
      : error instanceof ApiRequestError && error.status === 401
        ? 'invalidCredentials'
        : 'loginError'
    await nextTick()
    errorAlert.value?.focus()
  } finally {
    pending.value = false
  }
}

function resolveRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return '/'
  return value
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

      <form class="space-y-5" :aria-busy="pending" novalidate data-testid="admin-login-form" @submit.prevent="submit">
        <div class="space-y-2">
          <label for="admin-login-email" class="text-sm font-medium text-text-display">{{ t('auth.email') }}</label>
          <input
            id="admin-login-email"
            ref="emailInput"
            v-model="email"
            type="email"
            name="email"
            autocomplete="username"
            :readonly="pending"
            :aria-invalid="errors.email ? 'true' : 'false'"
            :aria-describedby="errors.email ? 'admin-login-email-error' : undefined"
            data-testid="admin-login-email"
            class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4 text-text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display"
          >
          <p v-if="errors.email" id="admin-login-email-error" class="text-sm text-error">{{ t(`auth.${errors.email}`) }}</p>
        </div>

        <div class="space-y-2">
          <label for="admin-login-password" class="text-sm font-medium text-text-display">{{ t('auth.password') }}</label>
          <div class="relative">
            <input
              id="admin-login-password"
              ref="passwordInput"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              name="password"
              autocomplete="current-password"
              :readonly="pending"
              :aria-invalid="errors.password ? 'true' : 'false'"
              :aria-describedby="errors.password ? 'admin-login-password-error' : undefined"
              data-testid="admin-login-password"
              class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4 pr-24 text-text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display"
            >
            <button
              type="button"
              :aria-label="t(showPassword ? 'auth.hidePassword' : 'auth.showPassword')"
              :aria-pressed="showPassword"
              :disabled="pending"
              class="absolute right-2 top-1/2 min-h-9 -translate-y-1/2 rounded-xl px-3 text-sm font-medium text-text-body disabled:opacity-60"
              @click="showPassword = !showPassword"
            >
              {{ t(showPassword ? 'auth.hide' : 'auth.show') }}
            </button>
          </div>
          <p v-if="errors.password" id="admin-login-password-error" class="text-sm text-error">{{ t(`auth.${errors.password}`) }}</p>
        </div>

        <p v-if="formError" ref="errorAlert" role="alert" tabindex="-1" data-testid="admin-login-error" class="rounded-2xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error">
          {{ t(`auth.${formError}`) }}
        </p>

        <button type="submit" :disabled="pending" class="min-h-11 w-full rounded-2xl bg-text-display px-4 font-semibold text-bg-primary disabled:opacity-60">
          {{ t(pending ? 'auth.signingIn' : 'auth.signIn') }}
        </button>
      </form>
    </section>
  </main>
</template>
