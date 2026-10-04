<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'
import { ensureApiSuccess, readApiJson } from '@/lib/api'

type BackendBuildInfo = {
  service: string
  version: string
  revision: string
  builtAt: string
}

const authStore = useAdminAuthStore()
const { locale, t } = useI18n()
const buildInfo = ref<BackendBuildInfo | null>(null)
const loading = ref(true)
const isKnown = (value?: string) =>
  Boolean(value?.trim() && value.trim().toLowerCase() !== 'unknown')
const hasVersion = computed(() => isKnown(buildInfo.value?.version))
const shortRevision = computed(() =>
  isKnown(buildInfo.value?.revision) ? buildInfo.value!.revision.slice(0, 7) : '',
)
const builtAt = computed(() => {
  const info = buildInfo.value
  if (!info || !isKnown(info.builtAt)) return ''
  const date = new Date(info.builtAt)
  if (Number.isNaN(date.getTime())) return info.builtAt
  return new Intl.DateTimeFormat(locale.value, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
})
const title = computed(() =>
  buildInfo.value && hasVersion.value
    ? [
        isKnown(buildInfo.value.revision) &&
          `${t('system.backendRevision')}: ${buildInfo.value.revision}`,
        builtAt.value && `${t('system.backendBuiltAt')}: ${builtAt.value}`,
      ].filter(Boolean).join('\n') || `API v${buildInfo.value.version}`
    : t('system.backendUnavailable'),
)

onMounted(async () => {
  try {
    const response = await authStore.request('/api/admin/system/build-info')
    await ensureApiSuccess(response)
    buildInfo.value = await readApiJson<BackendBuildInfo>(response)
  } catch {
    buildInfo.value = null
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section
    role="group"
    class="flex min-h-6 min-w-0 items-center gap-2 text-[10px]"
    :title="title"
    data-testid="backend-version-badge"
    :aria-label="t('system.backendBuild')"
  >
    <span class="label-mono shrink-0 text-[9px] text-text-secondary">API</span>
    <p
      v-if="buildInfo && hasVersion"
      class="flex min-w-0 items-center gap-2 font-mono text-text-secondary"
    >
      <span class="truncate">v{{ buildInfo.version }}</span>
      <span v-if="shortRevision" class="shrink-0">{{ shortRevision }}</span>
    </p>
    <output v-else aria-live="polite" class="min-w-0 truncate text-text-secondary">
      {{ loading ? t('common.loading') : t('system.backendUnavailable') }}
    </output>
  </section>
</template>
