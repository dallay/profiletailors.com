<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { resetPassword, type ApiError } from '@modules/auth/infrastructure/auth-api'
import { usePublicCapabilitiesStore } from '@modules/auth/infrastructure/public-capabilities.store'
import { resetPasswordSchema } from '@shared/lib/validation/schemas'
import AuthShell from './AuthShell.vue'
import PasswordRecoveryUnavailable from './PasswordRecoveryUnavailable.vue'

const TOKEN_ERROR_CODES = new Set([
  'INVALID_PASSWORD_RESET_TOKEN',
  'EXPIRED_PASSWORD_RESET_TOKEN',
  'USED_PASSWORD_RESET_TOKEN',
])

const route = useRoute()
const { t } = useI18n()
const capabilities = usePublicCapabilitiesStore()
onMounted(() => { void capabilities.load() })
const password = ref('')
const confirmPassword = ref('')
const pending = ref(false)
const status = ref<'form' | 'invalid' | 'success'>('form')
const fieldErrors = ref<{ password?: string; confirmPassword?: string }>({})
const requestError = ref<string | null>(null)

const token = computed(() => {
  const value = route.query.token
  return typeof value === 'string' && value.trim() ? value : null
})

if (!token.value) status.value = 'invalid'

function invalidState(): void {
  status.value = 'invalid'
  password.value = ''
  confirmPassword.value = ''
}

async function submit(): Promise<void> {
  if (pending.value || !token.value) return
  fieldErrors.value = {}
  requestError.value = null
  const result = resetPasswordSchema.safeParse({
    password: password.value,
    confirmPassword: confirmPassword.value,
  })
  if (!result.success) {
    const errors = result.error.flatten().fieldErrors
    fieldErrors.value = { password: errors.password?.[0], confirmPassword: errors.confirmPassword?.[0] }
    return
  }
  pending.value = true
  try {
    await resetPassword({ token: token.value, newPassword: result.data.password })
    status.value = 'success'
    password.value = ''
    confirmPassword.value = ''
  } catch (error) {
    const apiError = error as ApiError
    if (apiError.code && TOKEN_ERROR_CODES.has(apiError.code)) invalidState()
    else if (apiError.status === 429 || apiError.code === 'AUTH_RATE_LIMIT_EXCEEDED') requestError.value = 'passwordRecovery.rateLimited'
    else if (apiError.status === 503 || apiError.code === 'PASSWORD_RECOVERY_DISABLED') requestError.value = 'passwordRecovery.unavailable'
    else requestError.value = 'passwordRecovery.genericError'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <AuthShell>
    <div v-if="!capabilities.resolved" role="status" class="text-center text-sm text-text-secondary">{{ t('passwordRecovery.checkingAvailability') }}</div>
    <PasswordRecoveryUnavailable v-else-if="!capabilities.passwordRecoveryEnabled" />
    <Card v-else class="border-0 bg-transparent shadow-none">
      <CardHeader>
        <CardTitle>{{ t(status === 'success' ? 'passwordRecovery.resetSuccessTitle' : status === 'invalid' ? 'passwordRecovery.invalidLinkTitle' : 'passwordRecovery.resetTitle') }}</CardTitle>
        <CardDescription v-if="status === 'form'">{{ t('passwordRecovery.resetDescription') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="status === 'invalid'" class="flex flex-col gap-6">
          <p role="alert" class="text-sm leading-6 text-text-secondary">{{ t('passwordRecovery.invalidLinkMessage') }}</p>
          <Button as-child><RouterLink to="/forgot-password">{{ t('passwordRecovery.requestNewLink') }}</RouterLink></Button>
        </div>
        <div v-else-if="status === 'success'" class="flex flex-col gap-6">
          <output aria-live="polite" class="text-sm leading-6 text-text-secondary">{{ t('passwordRecovery.resetSuccessMessage') }}</output>
          <Button as-child><RouterLink to="/login">{{ t('passwordRecovery.signIn') }}</RouterLink></Button>
        </div>
        <form v-else class="flex flex-col gap-5" @submit.prevent="submit">
          <div class="flex flex-col gap-2">
            <Label for="new-password">{{ t('passwordRecovery.newPasswordLabel') }}</Label>
            <Input id="new-password" v-model="password" name="new-password" type="password" autocomplete="new-password" minlength="8" maxlength="128" required :aria-invalid="fieldErrors.password ? 'true' : 'false'" :aria-describedby="fieldErrors.password ? 'new-password-error' : 'password-policy'" />
            <p id="password-policy" class="text-sm text-text-secondary">{{ t('passwordRecovery.passwordPolicy') }}</p>
            <p v-if="fieldErrors.password" id="new-password-error" role="alert" class="text-sm text-error">{{ t(`passwordRecovery.${fieldErrors.password}`) }}</p>
          </div>
          <div class="flex flex-col gap-2">
            <Label for="confirm-new-password">{{ t('passwordRecovery.confirmPasswordLabel') }}</Label>
            <Input id="confirm-new-password" v-model="confirmPassword" name="confirm-new-password" type="password" autocomplete="new-password" required :aria-invalid="fieldErrors.confirmPassword ? 'true' : 'false'" :aria-describedby="fieldErrors.confirmPassword ? 'confirm-password-error' : undefined" />
            <p v-if="fieldErrors.confirmPassword" id="confirm-password-error" role="alert" class="text-sm text-error">{{ t(`passwordRecovery.${fieldErrors.confirmPassword}`) }}</p>
          </div>
          <p v-if="requestError" role="alert" class="text-sm text-error">{{ t(requestError) }}</p>
          <Button type="submit" class="min-h-11 w-full" :disabled="pending">
            <Spinner v-if="pending" data-icon="inline-start" />
            {{ t(pending ? 'passwordRecovery.resetting' : 'passwordRecovery.resetPassword') }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </AuthShell>
</template>
