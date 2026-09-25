import { onMounted, onUnmounted, ref } from 'vue'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'

export type Connectivity = 'checking' | 'online' | 'offline' | 'api-unreachable'

export function useOnlineStatus() {
  const status = ref<Connectivity>('checking')

  async function probe(): Promise<void> {
    if (!navigator.onLine) {
      status.value = 'offline'
      return
    }
    try {
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 3000)
      const response = await fetch(resolveApiUrl('/api/capabilities/public'), {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store',
        signal: controller.signal,
      })
      clearTimeout(timeout)
      status.value = response.ok ? 'online' : 'api-unreachable'
    } catch {
      status.value = navigator.onLine ? 'api-unreachable' : 'offline'
    }
  }

  onMounted(() => {
    void probe()
    window.addEventListener('online', probe)
    window.addEventListener('offline', probe)
  })
  onUnmounted(() => {
    window.removeEventListener('online', probe)
    window.removeEventListener('offline', probe)
  })

  return { status, retry: probe }
}
