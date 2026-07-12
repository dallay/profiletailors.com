import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePublishingStore } from '@/stores/publishing'

export type LinkedInCallbackStatus = 'loading' | 'success' | 'error'

export function useLinkedInCallback() {
  const route = useRoute()
  const router = useRouter()
  const publishing = usePublishingStore()
  const { t } = useI18n()

  const status = ref<LinkedInCallbackStatus>('loading')
  const message = ref(t('linkedinCallback.loadingMessage'))

  const redirectUri = `${globalThis.location.origin}/integrations/linkedin/callback`

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
      await publishing.connectLinkedInPersonalProfile(redirectUri)
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
        redirectUri,
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

  return {
    status,
    message,
    retryConnection,
    processCallback,
  }
}
