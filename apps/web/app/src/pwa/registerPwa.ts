import { registerSW } from 'virtual:pwa-register'

let updateServiceWorker: ReturnType<typeof registerSW> | null = null

export function initPwa(onNeedRefresh: () => void): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  updateServiceWorker = registerSW({ onNeedRefresh })
}

export async function updatePwa(): Promise<void> {
  await updateServiceWorker?.(true)
}
