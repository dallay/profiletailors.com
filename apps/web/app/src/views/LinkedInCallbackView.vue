<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { CheckCircle2, Loader2, TriangleAlert } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { usePublishingStore } from '@/stores/publishing'

const route = useRoute()
const router = useRouter()
const publishing = usePublishingStore()
const { t } = useI18n()

const status = ref<'loading' | 'success' | 'error'>('loading')
const message = ref(t('linkedinCallback.loadingMessage'))

const redirectUri = computed(() => `${window.location.origin}/integrations/linkedin/callback`)

function firstQueryValue(value: unknown): string | null {
  if (Array.isArray(value)) {
    const first = value[0]
    return typeof first === 'string' ? first : null
  }
  return typeof value === 'string' ? value : null
}

async function retryConnection() {
  status.value = 'loading'
  message.value = t('linkedinCallback.retryingMessage')

  try {
    await publishing.connectLinkedInPersonalProfile(redirectUri.value)
  } catch (err) {
    status.value = 'error'
    message.value = err instanceof Error ? err.message : t('linkedinCallback.retryFailedMessage')
  }
}

async function processCallback() {
  const oauthError = firstQueryValue(route.query.error)
  const oauthErrorDescription = firstQueryValue(route.query.error_description)

  if (oauthError) {
    status.value = 'error'
    message.value = oauthErrorDescription || t('linkedinCallback.deniedMessage')
    return
  }

  const code = firstQueryValue(route.query.code)
  const state = firstQueryValue(route.query.state)

  if (!code || !state) {
    status.value = 'error'
    message.value = t('linkedinCallback.missingParamsMessage')
    return
  }

  status.value = 'loading'
  message.value = t('linkedinCallback.loadingMessage')

  try {
    await publishing.completeLinkedInConnectionFromCallback({
      code,
      state,
      redirectUri: redirectUri.value,
    })

    status.value = 'success'
    message.value = t('linkedinCallback.successMessage')

    await router.replace({
      path: '/settings',
      query: { connected: 'linkedin', panel: 'channels', provider: 'linkedin' },
    })
  } catch (err) {
    status.value = 'error'
    message.value = err instanceof Error ? err.message : t('linkedinCallback.failedMessage')
  }
}

onMounted(() => {
  void processCallback()
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
          {{ status === 'success' ? $t('linkedinCallback.successTitle') : status === 'error' ? $t('linkedinCallback.errorTitle') : $t('linkedinCallback.loadingTitle') }}
        </CardTitle>
      </CardHeader>

      <CardContent class="mt-6 space-y-6 p-0 text-center">
        <p class="text-sm leading-6 text-text-secondary" role="status">
          {{ message }}
        </p>

        <div v-if="status === 'error'" class="flex flex-col justify-center gap-3 sm:flex-row">
          <Button type="button" @click="retryConnection">
            {{ $t('linkedinCallback.tryAgain') }}
          </Button>
          <Button as="a" href="/settings" variant="outline" type="button">
            {{ $t('linkedinCallback.backToSettings') }}
          </Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
