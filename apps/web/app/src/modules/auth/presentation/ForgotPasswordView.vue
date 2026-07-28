<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { requestPasswordReset, type ApiError } from '@modules/auth/infrastructure/auth-api'
import { forgotPasswordSchema } from '@shared/lib/validation/schemas'

const { t } = useI18n()
const email = ref('')
const pending = ref(false)
const success = ref(false)
const fieldError = ref<string | null>(null)
const requestError = ref<string | null>(null)

function safeError(error: unknown): string {
  const apiError = error as ApiError
  if (apiError.status === 429 || apiError.code === 'AUTH_RATE_LIMIT_EXCEEDED') return 'passwordRecovery.rateLimited'
  if (apiError.status === 503 || apiError.code === 'PASSWORD_RECOVERY_DISABLED') return 'passwordRecovery.unavailable'
  return 'passwordRecovery.genericError'
}

async function submit(): Promise<void> {
  if (pending.value) return
  fieldError.value = null
  requestError.value = null
  const result = forgotPasswordSchema.safeParse({ email: email.value })
  if (!result.success) {
    fieldError.value = 'passwordRecovery.invalidEmail'
    return
  }
  pending.value = true
  try {
    await requestPasswordReset(result.data.email)
    success.value = true
  } catch (error) {
    requestError.value = safeError(error)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-bg-primary px-4 py-10 text-text-body dot-grid">
    <Card class="w-full max-w-lg border-border-subtle bg-bg-surface">
      <CardHeader>
        <CardTitle>{{ t('passwordRecovery.forgotTitle') }}</CardTitle>
        <CardDescription>{{ t('passwordRecovery.forgotDescription') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="success" class="flex flex-col gap-6">
          <output aria-live="polite" class="text-sm leading-6 text-text-secondary">
            {{ t('passwordRecovery.forgotSuccessMessage') }}
          </output>
          <Button as-child variant="outline"><RouterLink to="/login">{{ t('passwordRecovery.backToLogin') }}</RouterLink></Button>
        </div>
        <form v-else class="flex flex-col gap-5" @submit.prevent="submit">
          <div class="flex flex-col gap-2">
            <Label for="recovery-email">{{ t('passwordRecovery.emailLabel') }}</Label>
            <Input id="recovery-email" v-model="email" name="email" type="email" autocomplete="email" required :aria-invalid="fieldError ? 'true' : 'false'" :aria-describedby="fieldError ? 'recovery-email-error' : undefined" />
            <p v-if="fieldError" id="recovery-email-error" role="alert" class="text-sm text-error">{{ t(fieldError) }}</p>
          </div>
          <p v-if="requestError" role="alert" class="text-sm text-error">{{ t(requestError) }}</p>
          <Button type="submit" class="min-h-11 w-full" :disabled="pending">
            <Spinner v-if="pending" data-icon="inline-start" />
            {{ t(pending ? 'passwordRecovery.sending' : 'passwordRecovery.sendLink') }}
          </Button>
          <RouterLink class="min-h-11 text-center text-sm underline underline-offset-4" to="/login">{{ t('passwordRecovery.backToLogin') }}</RouterLink>
        </form>
      </CardContent>
    </Card>
  </main>
</template>
