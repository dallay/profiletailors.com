<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { type AuthCredentials, authCredentialsSchema, registerSchema } from '@shared/lib/validation/schemas'
import type { RegisterPayload } from '@modules/auth/infrastructure/auth-api'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const isRegisterMode = computed(() => route.name === 'register')
const alternateRoute = computed(() => isRegisterMode.value ? '/login' : '/register')

const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const confirmedAgeEligibility = ref(false)
const acceptedTerms = ref(false)
const formError = ref<string | null>(null)
const fieldErrors = ref<{ email?: string; password?: string; confirmPassword?: string; confirmedAgeEligibility?: string; acceptedTerms?: string }>({})

if (auth.error) {
  formError.value = auth.error
}

// Clear form and store state when navigating between /login and /register
// (both use the same AuthView component, so setup() runs only once)
watch(() => route.name, () => {
  formError.value = null
  fieldErrors.value = {}
  email.value = ''
  password.value = ''
  confirmPassword.value = ''
  confirmedAgeEligibility.value = false
  acceptedTerms.value = false
  auth.clearError()
})

async function handleSubmit() {
  formError.value = null
  fieldErrors.value = {}
  auth.clearError()

  const validationResult = isRegisterMode.value
    ? registerSchema.safeParse({
        email: email.value,
        password: password.value,
        confirmPassword: confirmPassword.value,
        confirmedAgeEligibility: confirmedAgeEligibility.value,
        acceptedTerms: acceptedTerms.value,
      })
    : authCredentialsSchema.safeParse({ email: email.value, password: password.value })

  if (!validationResult.success) {
    const errors = validationResult.error.flatten().fieldErrors
    const confirmPasswordErrors =
      'confirmPassword' in errors && Array.isArray(errors.confirmPassword)
        ? errors.confirmPassword
        : undefined
    fieldErrors.value = {
      email: errors.email?.[0],
      password: errors.password?.[0],
      confirmPassword: confirmPasswordErrors?.[0],
      confirmedAgeEligibility: errors.confirmedAgeEligibility?.[0],
      acceptedTerms: errors.acceptedTerms?.[0],
    }
    return
  }

  const payload = validationResult.data

  try {
    if (isRegisterMode.value) {
      // confirmPassword is only for client-side validation; not sent to the server
      const { email, password } = payload as AuthCredentials
      const registerPayload: RegisterPayload = {
        email,
        password,
        confirmedAgeEligibility: true,
        acceptedTermsVersion: 'terms-v1.0.0',
      }
      await auth.registerWithPassword(registerPayload)
    } else {
      await auth.loginWithPassword(payload as AuthCredentials)
    }

    const redirectTo = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirectTo)
  } catch {
    formError.value = auth.error
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg-primary text-text-body dot-grid">
    <div class="mx-auto flex min-h-screen max-w-6xl items-center px-6 py-10 lg:px-10">
      <div class="grid w-full gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
        <section class="space-y-8">
          <div class="space-y-4">
            <div class="inline-flex items-center gap-2 rounded-full border border-border-visible bg-bg-surface px-4 py-2 font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
              <span class="size-2 rounded-full bg-text-display" />
              {{ $t('auth.badge') }}
            </div>
            <div class="space-y-3">
              <h1 class="max-w-xl text-4xl font-light tracking-tight text-text-display sm:text-5xl">
                {{ $t('auth.heroTitle') }}
              </h1>
              <p class="max-w-2xl text-sm leading-7 text-text-secondary sm:text-base">
                {{ $t('auth.heroDescription') }}
              </p>
            </div>
          </div>

          <div class="grid gap-4 sm:grid-cols-3">
            <div class="rounded-2xl border border-border-subtle bg-bg-surface p-5">
              <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
                {{ $t('auth.security') }}
              </p>
              <p class="mt-3 text-sm text-text-body">
                {{ $t('auth.securityDesc') }}
              </p>
            </div>
            <div class="rounded-2xl border border-border-subtle bg-bg-surface p-5">
              <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
                {{ $t('auth.focus') }}
              </p>
              <p class="mt-3 text-sm text-text-body">
                {{ $t('auth.focusDesc') }}
              </p>
            </div>
            <div class="rounded-2xl border border-border-subtle bg-bg-surface p-5">
              <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
                {{ $t('auth.workflow') }}
              </p>
              <p class="mt-3 text-sm text-text-body">
                {{ $t('auth.workflowDesc') }}
              </p>
            </div>
          </div>
        </section>

        <section class="rounded-[28px] border border-border-subtle bg-bg-surface p-7 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] sm:p-8">
          <div class="space-y-2">
            <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
              {{ $t('auth.localAccess') }}
            </p>
            <h2 class="text-2xl font-light text-text-display">
              {{ $t(isRegisterMode ? 'auth.titleRegister' : 'auth.titleLogin') }}
            </h2>
            <p class="text-sm leading-6 text-text-secondary">
              {{ $t(isRegisterMode ? 'auth.subtitleRegister' : 'auth.subtitleLogin') }}
            </p>
          </div>

          <form class="mt-8 space-y-5" @submit.prevent="handleSubmit">
            <div class="space-y-2">
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: for/id link is correct; biome cannot evaluate Vue i18n interpolation as label text -->
              <label class="font-mono text-[10px] font-bold uppercase tracking-[0.16em] text-text-secondary" for="email">
                {{ $t('auth.email') }}
              </label>
              <input
                id="email"
                v-model="email"
                type="email"
                autocomplete="email"
                :placeholder="$t('auth.emailPlaceholder', { at: '@' })"
                :aria-invalid="fieldErrors.email ? 'true' : 'false'"
                class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                required
              >
              <p v-if="fieldErrors.email" role="alert" class="text-sm text-error">
                {{ t(`auth.${fieldErrors.email}`) }}
              </p>
            </div>

            <div class="space-y-2">
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: for/id link is correct; biome cannot evaluate Vue i18n interpolation as label text -->
              <label class="font-mono text-[10px] font-bold uppercase tracking-[0.16em] text-text-secondary" for="password">
                {{ $t('auth.password') }}
              </label>
              <input
                id="password"
                v-model="password"
                type="password"
                autocomplete="current-password"
                :placeholder="$t('auth.passwordPlaceholder')"
                :aria-invalid="fieldErrors.password ? 'true' : 'false'"
                class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                required
              >
              <p v-if="fieldErrors.password" role="alert" class="text-sm text-error">
                {{ t(`auth.${fieldErrors.password}`) }}
              </p>
            </div>

            <div v-if="isRegisterMode" class="space-y-2">
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: for/id link is correct; biome cannot evaluate Vue i18n interpolation as label text -->
              <label class="font-mono text-[10px] font-bold uppercase tracking-[0.16em] text-text-secondary" for="confirmPassword">
                {{ $t('auth.confirmPassword') }}
              </label>
              <input
                id="confirmPassword"
                v-model="confirmPassword"
                type="password"
                autocomplete="new-password"
                :placeholder="$t('auth.confirmPasswordPlaceholder')"
                :aria-invalid="fieldErrors.confirmPassword ? 'true' : 'false'"
                class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                required
              >
              <p v-if="fieldErrors.confirmPassword" role="alert" class="text-sm text-error">
                {{ t(`auth.${fieldErrors.confirmPassword}`) }}
              </p>
            </div>

            <div v-if="isRegisterMode" class="space-y-4">
              <div class="flex cursor-pointer items-start gap-3">
                <input
                  id="ageEligibility"
                  v-model="confirmedAgeEligibility"
                  type="checkbox"
                  class="mt-0.5 size-4 shrink-0 rounded border-border-visible bg-bg-primary text-text-display focus:ring-2 focus:ring-text-display focus:ring-offset-2 focus:ring-offset-bg-primary"
                  :aria-invalid="fieldErrors.confirmedAgeEligibility ? 'true' : 'false'"
                  :aria-label="String($t('auth.ageEligibilityLabel'))"
                />
                <span class="text-sm leading-5 text-text-body">
                  {{ $t('auth.ageEligibilityLabel') }}
                </span>
              </div>
              <p v-if="fieldErrors.confirmedAgeEligibility" role="alert" class="pl-9 text-sm text-error">
                {{ t(`auth.${fieldErrors.confirmedAgeEligibility}`) }}
              </p>

              <div class="flex cursor-pointer items-start gap-3">
                <input
                  id="terms"
                  v-model="acceptedTerms"
                  type="checkbox"
                  class="mt-0.5 size-4 shrink-0 rounded border-border-visible bg-bg-primary text-text-display focus:ring-2 focus:ring-text-display focus:ring-offset-2 focus:ring-offset-bg-primary"
                  :aria-invalid="fieldErrors.acceptedTerms ? 'true' : 'false'"
                  :aria-label="String($t('auth.termsLabel'))"
                />
                <span class="text-sm leading-5 text-text-body">
                  {{ $t('auth.termsLabel') }}
                </span>
              </div>
              <p class="pl-9 text-xs text-text-secondary">
                <a href="/terms" class="underline hover:opacity-70 transition-opacity">Terms of Service</a>
                <span class="mx-1">&middot;</span>
                <a href="/privacy" class="underline hover:opacity-70 transition-opacity">Privacy Policy</a>
              </p>
              <p v-if="fieldErrors.acceptedTerms" role="alert" class="pl-9 text-sm text-error">
                {{ t(`auth.${fieldErrors.acceptedTerms}`) }}
              </p>
            </div>

            <div
              v-if="formError"
              role="alert"
              class="rounded-2xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error"
            >
              {{ formError }}
            </div>

            <Button type="submit" class="w-full justify-center" :disabled="auth.isLoading">
              {{ auth.isLoading ? '...' : $t(isRegisterMode ? 'auth.submitRegister' : 'auth.submitLogin') }}
            </Button>
          </form>

          <div class="mt-6 flex items-center justify-between gap-4 border-t border-border-subtle pt-5 text-sm">
            <span class="text-text-secondary">{{ $t(isRegisterMode ? 'auth.alternateLabelRegister' : 'auth.alternateLabelLogin') }}</span>
            <RouterLink
              :to="alternateRoute"
              class="font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-text-display transition-opacity hover:opacity-70"
            >
              {{ $t(isRegisterMode ? 'auth.alternateActionRegister' : 'auth.alternateActionLogin') }}
            </RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
