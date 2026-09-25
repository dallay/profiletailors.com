import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

function isIosSafari(): boolean {
  const ua = navigator.userAgent
  const isIos =
    /iPad|iPhone|iPod/.test(ua) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  const isSafari = /^((?!chrome|android).)*safari/i.test(ua)
  return isIos && isSafari && !(navigator as { standalone?: boolean }).standalone
}

export function usePwaInstall() {
  const deferred = ref<BeforeInstallPromptEvent | null>(null)
  const showIosGuide = ref(false)

  function onPrompt(event: Event): void {
    event.preventDefault()
    deferred.value = event as BeforeInstallPromptEvent
  }

  onMounted(() => {
    window.addEventListener('beforeinstallprompt', onPrompt)
    showIosGuide.value = isIosSafari()
  })
  onBeforeUnmount(() => {
    window.removeEventListener('beforeinstallprompt', onPrompt)
  })

  const canInstall = computed(() => deferred.value !== null)

  async function install(): Promise<void> {
    const event = deferred.value
    if (!event) return
    await event.prompt()
    await event.userChoice
    deferred.value = null
  }

  return { canInstall, install, showIosGuide }
}
