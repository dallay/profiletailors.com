<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t, locale } = useI18n()
const authStore = useAdminAuthStore()

interface AuditEvent {
  eventId: string
  occurredAt: string
  action: string
  targetType: string
  targetId: string
  result: string
}

interface PagedResult<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

const result = ref<PagedResult<AuditEvent> | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const page = ref(0)
const actionFilter = ref('')
const resultFilter = ref('')

let activeRequest: AbortController | null = null

async function fetchEvents() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '25' })
    if (actionFilter.value) params.set('action', actionFilter.value)
    if (resultFilter.value) params.set('result', resultFilter.value)
    const res = await authStore.request(`/api/admin/audit-events?${params}`, { signal: controller.signal })
    if (!res.ok) throw new Error()
    result.value = await res.json()
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

watch([actionFilter, resultFilter], () => { page.value = 0; fetchEvents() })
onMounted(fetchEvents)
onBeforeUnmount(() => activeRequest?.abort())
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-text-display">{{ t('audit.title') }}</h1>

    <div class="flex gap-3 mb-4">
      <input
        v-model="actionFilter"
        type="text"
        placeholder="Filter by action"
        class="admin-input w-48 text-sm"
        :aria-label="t('audit.action')"
      />
      <select
        v-model="resultFilter"
        class="admin-input text-sm"
        :aria-label="t('audit.result')"
      >
        <option value="">All results</option>
        <option value="SUCCEEDED">{{ t('audit.results.succeeded') }}</option>
        <option value="REJECTED">{{ t('audit.results.rejected') }}</option>
        <option value="FAILED">{{ t('audit.results.failed') }}</option>
      </select>
    </div>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-error">{{ error }}</div>
    <template v-else-if="result">
      <table class="admin-table w-full text-left text-sm" aria-label="Audit events">
        <thead>
          <tr class="border-b border-border-subtle text-text-secondary uppercase text-xs">
            <th scope="col" class="py-2 pr-4">{{ t('audit.occurredAt') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('audit.action') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('audit.targetType') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('audit.targetId') }}</th>
            <th scope="col" class="py-2">{{ t('audit.result') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="event in result.items"
            :key="event.eventId"
            class="border-b border-border-subtle hover:bg-bg-surface"
          >
            <td class="py-2 pr-4 text-text-secondary">{{ new Date(event.occurredAt).toLocaleString(locale) }}</td>
            <td class="py-2 pr-4 font-mono text-text-body text-xs">{{ event.action }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ event.targetType }}</td>
            <td class="max-w-32 truncate py-2 pr-4 font-mono text-text-secondary text-xs">{{ event.targetId }}</td>
            <td class="py-2">
              <span
                class="status-badge"
                :class="{
                  'bg-success/15 text-success': event.result === 'SUCCEEDED',
                  'bg-error/15 text-error': event.result === 'FAILED',
                  'bg-warning/15 text-warning': event.result === 'REJECTED',
                }"
              >
                {{ t(`audit.results.${event.result.toLowerCase()}`) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="mt-4 flex items-center justify-between text-sm text-text-secondary">
        <span>{{ t('common.page') }} {{ result.page + 1 }} {{ t('common.of') }} {{ result.totalPages }}</span>
        <div class="flex gap-2">
          <button
            :disabled="!result.hasPrevious"
            class="admin-button-secondary disabled:opacity-40"
            @click="page--; fetchEvents()"
          >{{ t('common.previous') }}</button>
          <button
            :disabled="!result.hasNext"
            class="admin-button-secondary disabled:opacity-40"
            @click="page++; fetchEvents()"
          >{{ t('common.next') }}</button>
        </div>
      </div>
    </template>
  </div>
</template>
