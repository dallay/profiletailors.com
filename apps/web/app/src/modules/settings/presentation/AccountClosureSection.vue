<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { closeAccount } from '@modules/auth/infrastructure/auth-api'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()

const confirmation = ref('')
const closing = ref(false)
const errorMessage = ref<string | null>(null)

async function handleCloseAccount() {
  if (confirmation.value !== 'DELETE') {
    errorMessage.value = t('settings.accountClosure.invalidConfirmation')
    return
  }

  const token = auth.accessToken
  if (!token) {
    errorMessage.value = t('settings.accountClosure.error')
    return
  }

  closing.value = true
  errorMessage.value = null

  try {
    await closeAccount(token)
    await auth.logout()
    await router.replace('/login')
  } catch (err) {
    const apiError = err as { status?: number; detail?: string }
    if (apiError.status === 429) {
      errorMessage.value = t('settings.accountClosure.rateLimited')
    } else {
      errorMessage.value = apiError.detail ?? t('settings.accountClosure.error')
    }
  } finally {
    closing.value = false
  }
}
</script>

<template>
  <Card
    data-testid="settings-account-closure-panel"
    class="border border-error/40 bg-error/[0.03] p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]"
  >
    <CardHeader class="space-y-3 border-b border-error/20 p-0 pb-5">
      <CardTitle class="label-mono text-[10px] text-error">
        {{ t('settings.accountClosure.title') }}
      </CardTitle>
      <p class="max-w-lg text-sm leading-6 text-text-secondary">
        {{ t('settings.accountClosure.description') }}
      </p>
    </CardHeader>

    <CardContent class="mt-6 space-y-4 p-0">
      <!-- biome-ignore lint/a11y/noLabelWithoutControl: $t() provides accessible text, Biome can't resolve i18n keys statically -->
      <label for="closure-confirmation-input" class="sr-only">
        {{ t('settings.accountClosure.confirmationLabel') }}
      </label>
      <input
        id="closure-confirmation-input"
        v-model="confirmation"
        type="text"
        :placeholder="t('settings.accountClosure.confirmationPlaceholder')"
        class="w-full rounded-2xl border border-error/40 bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-error focus:outline-none"
        autocomplete="off"
        @keyup.enter="handleCloseAccount"
      />

      <p v-if="errorMessage" role="alert" class="text-sm text-error" data-testid="closure-error">
        {{ errorMessage }}
      </p>

      <Button
        type="button"
        variant="destructive"
        :disabled="closing || confirmation !== 'DELETE'"
        data-testid="closure-submit-button"
        @click="handleCloseAccount"
      >
        {{ closing ? t('settings.accountClosure.closingButton') : t('settings.accountClosure.closeButton') }}
      </Button>
    </CardContent>
  </Card>
</template>
