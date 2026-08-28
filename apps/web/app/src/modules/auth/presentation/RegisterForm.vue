<script setup lang="ts">
import { nextTick, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { registerSchema } from '@shared/lib/validation/schemas'
import PasswordField from './PasswordField.vue'

const props = defineProps<{ email: string; invitationToken?: string }>()
const emit = defineEmits<{ 'update:email': [value: string]; success: [] }>()
const { t } = useI18n()
const auth = useAuthStore()
const password = ref('')
const confirmPassword = ref('')
const confirmedAgeEligibility = ref(false)
const acceptedTerms = ref(false)
const pending = ref(false)
const errors = ref<Record<string, string | undefined>>({})
const formError = ref(false)
const emailInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<{ focus: () => void } | null>(null)
const confirmPasswordInput = ref<{ focus: () => void } | null>(null)
const ageEligibilityInput = ref<HTMLInputElement | null>(null)
const termsInput = ref<HTMLInputElement | null>(null)
const errorAlert = ref<HTMLElement | null>(null)

async function submit() {
  if (pending.value) return
  errors.value = {}
  formError.value = false
  auth.clearError()
  const result = registerSchema.safeParse({
    email: props.email,
    password: password.value,
    confirmPassword: confirmPassword.value,
    confirmedAgeEligibility: confirmedAgeEligibility.value,
    acceptedTerms: acceptedTerms.value,
  })
  if (!result.success) {
    const fields = result.error.flatten().fieldErrors
    errors.value = Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, value?.[0]]))
    await nextTick()
    const invalidFields: Array<[
      field: string,
      input: Ref<{ focus: () => void } | null>,
    ]> = [
      ['email', emailInput],
      ['password', passwordInput],
      ['confirmPassword', confirmPasswordInput],
      ['confirmedAgeEligibility', ageEligibilityInput],
      ['acceptedTerms', termsInput],
    ]
    invalidFields.find(([field]) => errors.value[field])?.[1].value?.focus()
    return
  }
  pending.value = true
  try {
    await auth.registerWithPassword({
      email: result.data.email,
      password: result.data.password,
      confirmedAgeEligibility: true,
      acceptedTermsVersion: 'terms-v1.0.0',
      invitationToken: props.invitationToken,
    })
    emit('success')
  } catch {
    formError.value = true
    await nextTick()
    errorAlert.value?.focus()
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <form class="space-y-5" :aria-busy="pending" novalidate @submit.prevent="submit">
    <div class="space-y-2">
      <label for="register-email" class="text-sm font-medium text-text-display">{{ t('auth.email') }}</label>
      <input id="register-email" ref="emailInput" :value="email" type="email" autocomplete="username" :readonly="pending" :aria-invalid="errors.email ? 'true' : 'false'" :aria-describedby="errors.email ? 'register-email-error' : undefined" class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display" @input="emit('update:email', ($event.target as HTMLInputElement).value)">
      <p v-if="errors.email" id="register-email-error" class="text-sm text-error">{{ t(`auth.${errors.email}`) }}</p>
    </div>
    <PasswordField id="register-password" ref="passwordInput" v-model="password" :label="t('auth.password')" autocomplete="new-password" :error="errors.password ? t(`auth.${errors.password}`) : undefined" :readonly="pending" />
    <PasswordField id="confirm-password" ref="confirmPasswordInput" v-model="confirmPassword" :label="t('auth.confirmPassword')" autocomplete="new-password" :error="errors.confirmPassword ? t(`auth.${errors.confirmPassword}`) : undefined" :readonly="pending" />
    <label for="ageEligibility" class="flex items-start gap-3 text-sm"><input id="ageEligibility" ref="ageEligibilityInput" v-model="confirmedAgeEligibility" type="checkbox" :disabled="pending">{{ t('auth.ageEligibilityLabel') }}</label>
    <p v-if="errors.confirmedAgeEligibility" class="text-sm text-error">{{ t(`auth.${errors.confirmedAgeEligibility}`) }}</p>
    <label for="terms" class="flex items-start gap-3 text-sm"><input id="terms" ref="termsInput" v-model="acceptedTerms" type="checkbox" :disabled="pending">{{ t('auth.termsLabel') }}</label>
    <p v-if="errors.acceptedTerms" class="text-sm text-error">{{ t(`auth.${errors.acceptedTerms}`) }}</p>
    <p v-if="formError" ref="errorAlert" role="alert" tabindex="-1" class="rounded-2xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error">{{ t('auth.genericError') }}</p>
    <button type="submit" :disabled="pending" class="min-h-11 w-full rounded-2xl bg-text-display px-4 font-semibold text-bg-primary disabled:opacity-60">{{ t(pending ? 'auth.creatingAccount' : 'auth.submitRegister') }}</button>
    <RouterLink v-if="!pending" data-testid="login-navigation" :to="{ name: 'login' }" class="block text-center text-sm underline underline-offset-4">{{ t('auth.alternateActionRegister') }}</RouterLink>
    <span v-else data-testid="login-navigation" aria-disabled="true" class="block text-center text-sm opacity-60">{{ t('auth.alternateActionRegister') }}</span>
  </form>
</template>
