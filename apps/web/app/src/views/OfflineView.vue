<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useOnlineStatus } from '@/pwa/useOnlineStatus'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { status, retry } = useOnlineStatus()
const isRetrying = ref(false)

const redirectTarget = computed(() => {
  const raw = route.query.redirect
  if (Array.isArray(raw)) return raw[0] ?? '/'
  return typeof raw === 'string' && raw.startsWith('/') ? raw : '/'
})

async function handleRetry(): Promise<void> {
  isRetrying.value = true
  try {
    await retry()
    if (!auth.sessionChecked) await auth.hydrateSession()
    else if (auth.bootstrapState === 'unreachable') await auth.hydrateSession()
    if (auth.isAuthenticated) await router.replace(redirectTarget.value)
  } finally {
    isRetrying.value = false
  }
}

function goLogin(): void {
  void router.replace({ path: '/login', query: { redirect: redirectTarget.value } })
}
</script>

<template>
  <main
    class="mx-auto flex min-h-svh w-full max-w-md flex-col items-center justify-center gap-4 px-6 pt-[env(safe-area-inset-top)] pb-[calc(env(safe-area-inset-bottom)+1.5rem)]"
  >
    <p class="font-mono text-[10px] uppercase tracking-[0.18em] text-text-secondary">offline</p>
    <h1 class="text-xl font-semibold text-text-display">{{ t('pwa.offline.title') }}</h1>
    <p class="text-center text-sm text-text-secondary">{{ t('pwa.offline.description') }}</p>
    <p
      v-if="status === 'api-unreachable'"
      class="text-center text-sm text-amber-200"
    >
      {{ t('pwa.offline.apiUnreachable') }}
    </p>
    <button
      type="button"
      :disabled="isRetrying"
      class="w-full rounded-xl bg-bg-primary px-4 py-3 text-sm font-medium text-text-display disabled:opacity-60"
      @click="handleRetry"
    >
      {{ isRetrying ? t('pwa.offline.retrying') : t('pwa.offline.retry') }}
    </button>
    <button
      type="button"
      class="text-sm text-text-secondary underline"
      @click="goLogin"
    >
      {{ t('pwa.offline.backToLogin') }}
    </button>
  </main>
</template>
