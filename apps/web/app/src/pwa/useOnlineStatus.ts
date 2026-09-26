import { onMounted, onUnmounted, ref, type Ref } from 'vue'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'

export type Connectivity = 'checking' | 'online' | 'offline' | 'api-unreachable'

export interface UseOnlineStatusReturn {
  status: Ref<Connectivity>
  retry: () => Promise<void>
}

export function useOnlineStatus(): UseOnlineStatusReturn {
  const status = ref<Connectivity>('checking')
  let generation = 0

  async function probe(): Promise<void> {
    const request = ++generation
    if (!navigator.onLine) {
      status.value = 'offline'
      return
    }
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 3000)
    try {
      const response = await fetch(resolveApiUrl('/api/capabilities/public'), {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store',
        signal: controller.signal,
      })
      if (request === generation) status.value = response.ok ? 'online' : 'api-unreachable'
    } catch {
      if (request === generation) status.value = navigator.onLine ? 'api-unreachable' : 'offline'
    } finally {
      clearTimeout(timeout)
    }
  }

  onMounted(() => {
    void probe()
    window.addEventListener('online', probe)
    window.addEventListener('offline', probe)
  })
  onUnmounted(() => {
    generation++
    window.removeEventListener('online', probe)
    window.removeEventListener('offline', probe)
  })

  return { status, retry: probe }
}
