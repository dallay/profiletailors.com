<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRegisterSW } from 'virtual:pwa-register/vue'

const { t } = useI18n()
const visible = ref(false)
let showHandler: (() => void) | null = null

const { updateServiceWorker } = useRegisterSW({
  onNeedRefresh() {
    visible.value = true
  },
})

function onNeedRefresh(): void {
  visible.value = true
}

onMounted(() => {
  showHandler = onNeedRefresh
  window.addEventListener('pwa:need-refresh', showHandler)
})

onUnmounted(() => {
  if (showHandler) window.removeEventListener('pwa:need-refresh', showHandler)
})

async function refresh(): Promise<void> {
  visible.value = false
  await updateServiceWorker(true)
}

function dismiss(): void {
  visible.value = false
}
</script>

<template>
  <div
    v-if="visible"
    role="status"
    class="fixed inset-x-4 bottom-[calc(env(safe-area-inset-bottom)+1rem)] z-50 mx-auto max-w-md rounded-xl border border-border-visible bg-bg-surface px-4 py-3 shadow-2xl"
  >
    <p class="text-sm text-text-display">{{ t('pwa.update.message') }}</p>
    <div class="mt-2 flex gap-2">
      <button
        type="button"
        class="rounded-lg bg-bg-primary px-3 py-2 text-sm font-medium text-text-display"
        @click="refresh"
      >
        {{ t('pwa.update.refresh') }}
      </button>
      <button
        type="button"
        class="rounded-lg border border-border-visible px-3 py-2 text-sm text-text-secondary"
        @click="dismiss"
      >
        {{ t('pwa.update.dismiss') }}
      </button>
    </div>
  </div>
</template>
