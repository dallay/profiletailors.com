<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

const { t } = useI18n()
const auth = useAuthStore()

const isResending = computed(() => auth.resendVerificationStatus === 'loading')

const feedback = computed(() => {
  if (auth.resendVerificationStatus === 'success') return t('emailVerification.banner.success')
  if (auth.resendVerificationStatus === 'error') {
    return auth.resendVerificationError ?? t('emailVerification.banner.error')
  }
  return null
})

async function handleResend() {
  try {
    await auth.resendVerificationEmail()
  } catch (err) {
    console.error('Failed to resend verification email', err)
  }
}
</script>

<template>
  <section
    role="alert"
    aria-label="Email verification required"
    class="mx-4 mt-4 rounded-xl border border-amber-400/40 bg-amber-400/10 px-4 py-3 text-sm text-amber-50 md:mx-6 lg:mx-8"
  >
    <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
      <div class="space-y-1">
        <p class="font-semibold text-amber-100">
          {{ t('emailVerification.banner.title') }}
        </p>
        <p>{{ t('emailVerification.banner.description') }}</p>
        <p class="text-amber-100/80">
          {{ t('emailVerification.banner.instructions') }}
        </p>
        <p
          v-if="feedback"
          aria-live="polite"
          class="font-medium text-amber-100"
        >
          {{ feedback }}
        </p>
      </div>

      <button
        type="button"
        data-testid="resend-verification"
        class="inline-flex items-center justify-center rounded-lg border border-amber-300/60 px-3 py-2 font-medium text-amber-50 transition hover:bg-amber-300/10 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="isResending"
        @click="handleResend"
      >
        {{ isResending ? t('emailVerification.banner.resending') : t('emailVerification.banner.resend') }}
      </button>
    </div>
  </section>
</template>
