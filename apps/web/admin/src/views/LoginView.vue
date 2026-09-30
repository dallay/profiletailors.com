<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { ApiRequestError } from '@/lib/api'
import { useAdminAuthStore } from '@/stores/auth.store'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Field, FieldLabel, FieldError } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { EyeIcon, EyeOffIcon } from '@lucide/vue'

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
  const normalizedPassword = password.value

  if (!normalizedEmail) errors.value.email = 'emailRequired'
  else if (!/^\S+@\S+\.\S+$/.test(normalizedEmail)) errors.value.email = 'emailInvalid'
  if (!normalizedPassword.trim()) errors.value.password = 'passwordRequired'

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
    const targetEl = (errorAlert.value as unknown as { $el?: HTMLElement })?.$el ?? errorAlert.value
    targetEl?.focus()
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
  <main class="dot-grid flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
    <Card class="w-full max-w-md rounded-[28px] border-border bg-card p-2 sm:p-4">
      <CardHeader class="mb-4 flex flex-col items-center gap-3 text-center">
        <img :src="lightOnDarkLogoUrl" alt="" class="h-14 w-12" aria-hidden="true">
        <span class="text-xl font-semibold tracking-tight text-foreground">Profile Tailors</span>
        <p class="label-mono text-muted-foreground">{{ t('auth.platformAdmin') }}</p>
      </CardHeader>

      <CardContent>
        <form class="space-y-5" :aria-busy="pending" novalidate data-testid="admin-login-form" @submit.prevent="submit">
          <Field class="space-y-2">
            <FieldLabel for="admin-login-email" class="text-sm font-medium text-foreground">{{ t('auth.email') }}</FieldLabel>
            <Input
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
              class="min-h-11 rounded-2xl"
            />
            <FieldError v-if="errors.email" id="admin-login-email-error">{{ t(`auth.${errors.email}`) }}</FieldError>
          </Field>

          <Field class="space-y-2">
            <FieldLabel for="admin-login-password" class="text-sm font-medium text-foreground">{{ t('auth.password') }}</FieldLabel>
            <div class="relative">
              <Input
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
                class="min-h-11 rounded-2xl pr-24"
              />
              <Button
                type="button"
                variant="ghost"
                size="sm"
                :aria-label="t(showPassword ? 'auth.hidePassword' : 'auth.showPassword')"
                :aria-pressed="showPassword"
                :disabled="pending"
                class="absolute right-2 top-1/2 -translate-y-1/2 rounded-xl text-xs font-medium text-foreground"
                @click="showPassword = !showPassword"
              >
                <component :is="showPassword ? EyeOffIcon : EyeIcon" class="mr-1 size-3.5" aria-hidden="true" />
                {{ t(showPassword ? 'auth.hide' : 'auth.show') }}
              </Button>
            </div>
            <FieldError v-if="errors.password" id="admin-login-password-error">{{ t(`auth.${errors.password}`) }}</FieldError>
          </Field>

          <Alert v-if="formError" ref="errorAlert" variant="destructive" tabindex="-1" data-testid="admin-login-error" class="rounded-2xl">
            <AlertDescription>{{ t(`auth.${formError}`) }}</AlertDescription>
          </Alert>

          <Button type="submit" :disabled="pending" class="min-h-11 w-full rounded-2xl font-semibold">
            {{ t(pending ? 'auth.signingIn' : 'auth.signIn') }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </main>
</template>
