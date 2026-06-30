<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { CheckCircle2, Loader2, TriangleAlert } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { ApiError } from '@/lib/auth-api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n()

const status = ref<'loading' | 'success' | 'missing-token' | 'invalid' | 'expired'>('loading')
const message = ref(t('verifyEmail.loadingMessage'))

const successHref = computed(() => (auth.isAuthenticated ? '/' : '/login'))
const successCta = computed(() =>
  auth.isAuthenticated ? t('verifyEmail.dashboardCta') : t('verifyEmail.signInCta'),
)

function firstQueryValue(value: unknown): string | null {
  if (Array.isArray(value)) {
    const first = value[0]
    return typeof first === 'string' ? first : null
  }

  return typeof value === 'string' ? value : null
}

function resolveFailureState(error: ApiError | Error): 'invalid' | 'expired' {
  const detail = 'detail' in error && typeof error.detail === 'string' ? error.detail : ''
  return /expired/i.test(detail) ? 'expired' : 'invalid'
}

async function processVerification() {
  const token = firstQueryValue(route.query.token)

  if (!token) {
    status.value = 'missing-token'
    message.value = t('verifyEmail.missingTokenMessage')
    return
  }

  status.value = 'loading'
  message.value = t('verifyEmail.loadingMessage')

  try {
    await auth.verifyEmail(token)
    status.value = 'success'
    message.value = t('verifyEmail.successMessage')
  } catch (error_) {
    const apiError = error_ as ApiError | Error
    status.value = resolveFailureState(apiError)
    message.value =
      (('detail' in apiError && typeof apiError.detail === 'string') ? apiError.detail : null)
      ?? (status.value === 'expired'
        ? t('verifyEmail.expiredMessage')
        : t('verifyEmail.invalidMessage'))
  }
}

onMounted(() => {
  void processVerification()
})
</script>

<template>
  <div class="mx-auto flex min-h-[60vh] max-w-2xl items-center justify-center py-12">
    <Card class="w-full border border-border-subtle bg-bg-surface">
      <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-6 text-center">
        <div class="mx-auto flex size-12 items-center justify-center rounded-2xl border border-border-visible bg-bg-primary text-text-display">
          <Loader2 v-if="status === 'loading'" class="size-5 animate-spin" />
          <CheckCircle2 v-else-if="status === 'success'" class="size-5 text-success" />
          <TriangleAlert v-else class="size-5 text-error" />
        </div>

        <CardTitle class="text-2xl font-light tracking-tight text-text-display">
          {{
            status === 'success'
              ? $t('verifyEmail.successTitle')
              : status === 'missing-token'
                ? $t('verifyEmail.missingTokenTitle')
                : status === 'expired'
                  ? $t('verifyEmail.expiredTitle')
                  : status === 'invalid'
                    ? $t('verifyEmail.invalidTitle')
                    : $t('verifyEmail.loadingTitle')
          }}
        </CardTitle>
      </CardHeader>

      <CardContent class="mt-6 space-y-6 p-0 text-center">
        <output aria-live="polite" class="text-sm leading-6 text-text-secondary">
          {{ message }}
        </output>

        <div v-if="status === 'success'" class="flex justify-center">
          <Button as="a" :href="successHref" type="button">
            {{ successCta }}
          </Button>
        </div>

        <div v-else-if="status !== 'loading'" class="flex justify-center">
          <Button as="a" href="/login" variant="outline" type="button">
            {{ $t('verifyEmail.backToSignIn') }}
          </Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
