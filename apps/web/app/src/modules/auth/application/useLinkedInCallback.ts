import { ref, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'

export type LinkedInCallbackStatus = 'loading' | 'success' | 'error'

export type ProviderCallbackState = {
  status: Ref<LinkedInCallbackStatus>
  message: Ref<string>
  providerLabel: string
  retryConnection: () => Promise<void>
  processCallback: () => Promise<void>
}

export type LinkedInCallbackState = ProviderCallbackState

type CallbackProvider = 'linkedin' | 'threads'

function firstQueryValue(value: unknown): string | null {
  if (Array.isArray(value)) {
    const first = value[0]
    return typeof first === 'string' ? first : null
  }
  return typeof value === 'string' ? value : null
}

function resolveProvider(value: unknown): CallbackProvider | null {
  const requestedProvider = firstQueryValue(value)
  if (requestedProvider === null && (value === undefined || value === null)) {
    return 'linkedin'
  }
  if (requestedProvider === 'linkedin' || requestedProvider === 'threads') {
    return requestedProvider
  }
  return null
}

export function useLinkedInCallback(): LinkedInCallbackState {
  return useProviderCallback()
}

export function useProviderCallback(): ProviderCallbackState {
  const route = useRoute()
  const router = useRouter()
  const publishing = usePublishingStore()
  const { t } = useI18n()
  const provider = resolveProvider(route.params.provider)
  const providerLabel =
    provider === 'threads' ? 'Threads' : provider === 'linkedin' ? 'LinkedIn' : 'provider'
  const redirectUri = provider
    ? `${globalThis.location.origin}/integrations/${provider}/callback`
    : null
  const status = ref<LinkedInCallbackStatus>('loading')
  const message = ref(t('providerCallback.loadingMessage', { provider: providerLabel }))

  async function retryConnection(): Promise<void> {
    if (!provider || !redirectUri) {
      status.value = 'error'
      message.value = t('providerCallback.unsupportedProviderMessage')
      return
    }

    status.value = 'loading'
    message.value = t('providerCallback.retryingMessage', { provider: providerLabel })

    try {
      await publishing.connectProviderPersonalProfile(provider, redirectUri)
    } catch {
      status.value = 'error'
      message.value = t('providerCallback.retryFailedMessage', { provider: providerLabel })
    }
  }

  async function processCallback(): Promise<void> {
    if (!provider || !redirectUri) {
      status.value = 'error'
      message.value = t('providerCallback.unsupportedProviderMessage')
      return
    }

    const oauthError = firstQueryValue(route.query.error)
    if (oauthError) {
      status.value = 'error'
      message.value = t('providerCallback.deniedMessage', { provider: providerLabel })
      return
    }

    const code = firstQueryValue(route.query.code)
    const state = firstQueryValue(route.query.state)
    if (!code || !state) {
      status.value = 'error'
      message.value = t('providerCallback.missingParamsMessage', { provider: providerLabel })
      return
    }

    status.value = 'loading'
    message.value = t('providerCallback.loadingMessage', { provider: providerLabel })

    try {
      await publishing.completeProviderConnectionFromCallback({
        provider,
        code,
        state,
        redirectUri,
      })
      status.value = 'success'
      message.value = t('providerCallback.successMessage', { provider: providerLabel })
      await router.replace({
        path: '/settings',
        query: { connected: provider, panel: 'channels', provider },
      })
    } catch {
      status.value = 'error'
      message.value = t('providerCallback.failedMessage', { provider: providerLabel })
    }
  }

  return {
    status,
    message,
    providerLabel,
    retryConnection,
    processCallback,
  }
}
