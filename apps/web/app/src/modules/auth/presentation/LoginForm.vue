<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { authCredentialsSchema } from '@shared/lib/validation/schemas'
import PasswordField from './PasswordField.vue'

const props = defineProps<{ email: string; showForgotPassword?: boolean }>()
const emit = defineEmits<{ 'update:email': [value: string]; success: [] }>()
const { t } = useI18n()
const auth = useAuthStore()
const password = ref('')
const pending = ref(false)
const errors = ref<{ email?: string; password?: string }>({})
const formError = ref(false)
const emailInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)
const errorAlert = ref<HTMLElement | null>(null)

async function submit() {
  if (pending.value) return
  errors.value = {}
  formError.value = false
  auth.clearError()
  const result = authCredentialsSchema.safeParse({ email: props.email, password: password.value })
  if (!result.success) {
    const fields = result.error.flatten().fieldErrors
    errors.value = { email: fields.email?.[0], password: fields.password?.[0] }
    await nextTick()
    ;(errors.value.email ? emailInput.value : passwordInput.value)?.focus()
    return
  }
  pending.value = true
  try {
    await auth.loginWithPassword(result.data)
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
      <label for="login-email" class="text-sm font-medium text-text-display">{{ t('auth.email') }}</label>
      <input
        id="login-email"
        ref="emailInput"
        :value="email"
        type="email"
        autocomplete="username"
        :readonly="pending"
        :aria-invalid="errors.email ? 'true' : 'false'"
        :aria-describedby="errors.email ? 'login-email-error' : undefined"
        class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display"
        @input="emit('update:email', ($event.target as HTMLInputElement).value)"
      >
      <p v-if="errors.email" id="login-email-error" class="text-sm text-error">{{ t(`auth.${errors.email}`) }}</p>
    </div>
    <PasswordField id="login-password" ref="passwordInput" v-model="password" :label="t('auth.password')" :error="errors.password ? t(`auth.${errors.password}`) : undefined" :readonly="pending" />
    <RouterLink v-if="showForgotPassword && !pending" data-testid="forgot-password-navigation" :to="{ name: 'forgot-password' }" class="block text-right text-sm underline underline-offset-4">{{ t('auth.forgotPassword') }}</RouterLink>
    <span v-else-if="showForgotPassword" data-testid="forgot-password-navigation" aria-disabled="true" class="block text-right text-sm opacity-60">{{ t('auth.forgotPassword') }}</span>
    <p v-if="formError" ref="errorAlert" role="alert" tabindex="-1" class="rounded-2xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error">{{ t('auth.genericError') }}</p>
    <button type="submit" :disabled="pending" class="min-h-11 w-full rounded-2xl bg-text-display px-4 font-semibold text-bg-primary disabled:opacity-60">{{ t(pending ? 'auth.signingIn' : 'auth.submitLogin') }}</button>
  </form>
</template>
