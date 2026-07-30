<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { requestPasswordReset, type ApiError } from '@modules/auth/infrastructure/auth-api'
import { usePublicCapabilitiesStore } from '@modules/auth/infrastructure/public-capabilities.store'
import { forgotPasswordSchema } from '@shared/lib/validation/schemas'
import AuthShell from './AuthShell.vue'
import PasswordRecoveryUnavailable from './PasswordRecoveryUnavailable.vue'

const { t } = useI18n()
const capabilities = usePublicCapabilitiesStore()
const email = ref('')
const pending = ref(false)
const success = ref(false)
const fieldError = ref(false)
const requestError = ref<string | null>(null)
onMounted(() => { void capabilities.load() })

async function submit() {
  if (pending.value) return
  fieldError.value = false
  requestError.value = null
  const result = forgotPasswordSchema.safeParse({ email: email.value })
  if (!result.success) { fieldError.value = true; return }
  pending.value = true
  try {
    await requestPasswordReset(result.data.email)
    success.value = true
  } catch (cause) {
    const error = cause as ApiError
    requestError.value = error.status === 429
      ? 'passwordRecovery.rateLimited'
      : error.status === 503 || error.code === 'PASSWORD_RECOVERY_DISABLED'
        ? 'passwordRecovery.unavailable'
        : 'passwordRecovery.genericError'
  } finally { pending.value = false }
}
</script>

<template>
  <AuthShell>
    <div v-if="!capabilities.resolved" role="status" class="text-center text-sm text-text-secondary">{{ t('passwordRecovery.checkingAvailability') }}</div>
    <PasswordRecoveryUnavailable v-else-if="!capabilities.passwordRecoveryEnabled" />
    <div v-else class="space-y-6">
      <header class="space-y-2 text-center"><h1 class="text-2xl font-semibold text-text-display">{{ t('passwordRecovery.forgotTitle') }}</h1><p class="text-sm text-text-secondary">{{ t('passwordRecovery.forgotDescription') }}</p></header>
      <output v-if="success" aria-live="polite" class="block text-sm text-text-secondary">{{ t('passwordRecovery.forgotSuccessMessage') }}</output>
      <form v-else class="space-y-5" :aria-busy="pending" novalidate @submit.prevent="submit">
        <div class="space-y-2"><label for="recovery-email" class="text-sm font-medium">{{ t('passwordRecovery.emailLabel') }}</label><input id="recovery-email" v-model="email" type="email" autocomplete="email" :readonly="pending" :aria-invalid="fieldError" :aria-describedby="fieldError ? 'recovery-email-error' : undefined" class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4"><p v-if="fieldError" id="recovery-email-error" class="text-sm text-error">{{ t('passwordRecovery.invalidEmail') }}</p></div>
        <p v-if="requestError" role="alert" class="text-sm text-error">{{ t(requestError) }}</p>
        <button type="submit" :disabled="pending" class="min-h-11 w-full rounded-2xl bg-text-display text-bg-primary">{{ t(pending ? 'passwordRecovery.sending' : 'passwordRecovery.sendLink') }}</button>
      </form>
      <RouterLink :to="{ name: 'login' }" class="block text-center text-sm underline underline-offset-4">{{ t('passwordRecovery.backToLogin') }}</RouterLink>
    </div>
  </AuthShell>
</template>
