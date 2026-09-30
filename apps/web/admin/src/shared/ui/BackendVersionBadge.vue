<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAdminAuthStore } from '@/stores/auth.store'
import { ensureApiSuccess, readApiJson } from '@/lib/api'

type BackendBuildInfo = {
  service: string
  version: string
  revision: string
  builtAt: string
}

const authStore = useAdminAuthStore()
const buildInfo = ref<BackendBuildInfo | null>(null)
const shortRevision = computed(() => buildInfo.value?.revision.slice(0, 7) ?? '—')
const title = computed(() =>
  buildInfo.value
    ? `${buildInfo.value.revision}\n${buildInfo.value.builtAt}`
    : 'Backend build information unavailable',
)

onMounted(async () => {
  try {
    const response = await authStore.request('/api/admin/system/build-info')
    await ensureApiSuccess(response)
    buildInfo.value = await readApiJson<BackendBuildInfo>(response)
  } catch {
    buildInfo.value = null
  }
})
</script>

<template>
  <span
    class="font-mono text-[10px] uppercase tracking-wider text-text-secondary/70"
    :title="title"
    data-testid="backend-version-badge"
  >
    API v{{ buildInfo?.version ?? '—' }} · {{ shortRevision }}
  </span>
</template>
