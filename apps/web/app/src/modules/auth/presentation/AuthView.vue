<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import type { RegisterPayload } from '@modules/auth/infrastructure/auth-api'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { usePublicCapabilitiesStore } from '@modules/auth/infrastructure/public-capabilities.store'
import { useAuthForm } from '@modules/auth/application/useAuthForm'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const capabilities = usePublicCapabilitiesStore()

const {
  email,
  password,
  confirmPassword,
  confirmedAgeEligibility,
  acceptedTerms,
  formError,
  isRegisterMode,
  fieldErrors,
  validateForm,
  getFormPayload,
  setFormError,
  setSubmitting,
} = useAuthForm()

onMounted(() => {
  capabilities.load()
})

const alternateRoute = computed(() => (isRegisterMode.value ? '/login' : '/register'))
const showRegistrationLink = computed(
  () => !isRegisterMode.value && capabilities.capabilityChecked && capabilities.registrationEnabled,
)
const registrationClosed = computed(
  () => isRegisterMode.value && capabilities.capabilityChecked && !capabilities.registrationEnabled,
)

async function handleSubmit() {
  setFormError(null)
  auth.clearError()

  if (!validateForm()) {
    return
  }

  setSubmitting(true)

  try {
    const payload = getFormPayload()

    if (payload.type === 'register') {
      const registerPayload: RegisterPayload = {
        email: payload.data.email,
        password: payload.data.password,
        confirmedAgeEligibility: true,
        acceptedTermsVersion: 'terms-v1.0.0',
      }
      await auth.registerWithPassword(registerPayload)
    } else {
      await auth.loginWithPassword({
        email: payload.data.email,
        password: payload.data.password,
      })
    }

    const redirectTo = typeof router.currentRoute.value.query.redirect === 'string' ? router.currentRoute.value.query.redirect : '/'
    await router.replace(redirectTo)
  } catch (_err) {
    setFormError(auth.error || 'Unknown error')
  } finally {
    setSubmitting(false)
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
              <label for="ageEligibility" class="flex cursor-pointer items-start gap-3 text-sm leading-5 text-text-body">
                <input
                  id="ageEligibility"
                  v-model="confirmedAgeEligibility"
                  type="checkbox"
                  class="mt-0.5 size-4 shrink-0 rounded border-border-visible bg-bg-primary text-text-display focus:ring-2 focus:ring-text-display focus:ring-offset-2 focus:ring-offset-bg-primary"
                  :aria-invalid="fieldErrors.confirmedAgeEligibility ? 'true' : 'false'"
                />
                {{ $t('auth.ageEligibilityLabel') }}
              </label>
              <p v-if="fieldErrors.confirmedAgeEligibility" role="alert" class="pl-9 text-sm text-error">
                {{ t(`auth.${fieldErrors.confirmedAgeEligibility}`) }}
              </p>

              <label for="terms" class="flex cursor-pointer items-start gap-3 text-sm leading-5 text-text-body">
                <input
                  id="terms"
                  v-model="acceptedTerms"
                  type="checkbox"
                  class="mt-0.5 size-4 shrink-0 rounded border-border-visible bg-bg-primary text-text-display focus:ring-2 focus:ring-text-display focus:ring-offset-2 focus:ring-offset-bg-primary"
                  :aria-invalid="fieldErrors.acceptedTerms ? 'true' : 'false'"
                />
                {{ $t('auth.termsLabel') }}
              </label>
              <p class="pl-9 text-xs text-text-secondary">
                <a href="/terms" class="underline hover:opacity-70 transition-opacity">{{ $t('auth.termsOfService') }}</a>
                <span class="mx-1">&middot;</span>
                <a href="/privacy" class="underline hover:opacity-70 transition-opacity">{{ $t('auth.privacyPolicy') }}</a>
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
              v-if="isRegisterMode || showRegistrationLink"
              :to="alternateRoute"
              class="font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-text-display transition-opacity hover:opacity-70"
            >
              {{ $t(isRegisterMode ? 'auth.alternateActionRegister' : 'auth.alternateActionLogin') }}
            </RouterLink>
            <span v-else class="font-mono text-[11px] uppercase tracking-[0.12em] text-text-secondary">
              {{ $t('auth.registrationClosed') }}
            </span>
          </div>

          <div
            v-if="registrationClosed"
            role="alert"
            class="mt-4 rounded-2xl border border-text-secondary/20 bg-text-secondary/10 px-4 py-3 text-sm text-text-secondary"
          >
            {{ $t('auth.registrationClosedMessage') }}
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
