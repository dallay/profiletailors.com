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
const shortRevision = computed(() => buildInfo.value?.revision.slice(0, 7) ?? '—')
const builtAt = computed(() => {
  if (!buildInfo.value) return ''
  const date = new Date(buildInfo.value.builtAt)
  if (Number.isNaN(date.getTime())) return buildInfo.value.builtAt
  return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
})
const title = computed(() =>
  buildInfo.value
    ? `${t('system.backendRevision')}: ${buildInfo.value.revision}\n${t('system.backendBuiltAt')}: ${builtAt.value}`
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
    class="rounded-lg border border-border-subtle bg-bg-primary px-3 py-2"
    :title="title"
    data-testid="backend-version-badge"
    :aria-label="t('system.backendBuild')"
  >
    <p class="label-mono text-[10px] text-text-secondary">{{ t('system.backendBuild') }}</p>
    <p v-if="buildInfo" class="mt-1 flex items-center justify-between gap-2 font-mono text-[11px] text-text-body">
      <span>API v{{ buildInfo.version }}</span>
      <span class="text-text-secondary">{{ shortRevision }}</span>
    </p>
    <output v-else aria-live="polite" class="mt-1 block text-xs text-text-secondary">
      {{ loading ? t('common.loading') : t('system.backendUnavailable') }}
    </output>
    <p v-if="buildInfo && builtAt" class="mt-1 truncate text-[10px] text-text-secondary">{{ builtAt }}</p>
  </section>
</template>
