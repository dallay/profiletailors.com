<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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

async function fetchEvents() {
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '25' })
    if (actionFilter.value) params.set('action', actionFilter.value)
    if (resultFilter.value) params.set('result', resultFilter.value)
    const res = await fetch(`/api/admin/audit-events?${params}`)
    if (!res.ok) throw new Error()
    result.value = await res.json()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

watch([actionFilter, resultFilter], () => { page.value = 0; fetchEvents() })
onMounted(fetchEvents)
</script>

<template>
  <div class="p-8">
    <h1 class="text-2xl font-bold text-slate-100 mb-6">{{ t('audit.title') }}</h1>

    <div class="flex gap-3 mb-4">
      <input
        v-model="actionFilter"
        type="text"
        placeholder="Filter by action"
        class="bg-slate-800 border border-slate-700 text-slate-200 rounded-lg px-3 py-1.5 text-sm w-48"
        :aria-label="t('audit.action')"
      />
      <select
        v-model="resultFilter"
        class="bg-slate-800 border border-slate-700 text-slate-200 rounded-lg px-3 py-1.5 text-sm"
        :aria-label="t('audit.result')"
      >
        <option value="">All results</option>
        <option value="SUCCEEDED">{{ t('audit.results.succeeded') }}</option>
        <option value="REJECTED">{{ t('audit.results.rejected') }}</option>
        <option value="FAILED">{{ t('audit.results.failed') }}</option>
      </select>
    </div>

    <div v-if="loading" class="text-slate-400">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-red-400">{{ error }}</div>
    <template v-else-if="result">
      <table class="w-full text-sm text-left border-collapse" aria-label="Audit events">
        <thead>
          <tr class="border-b border-slate-800 text-slate-400 uppercase text-xs">
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
            class="border-b border-slate-800/50 hover:bg-slate-900/50"
          >
            <td class="py-2 pr-4 text-slate-400">{{ new Date(event.occurredAt).toLocaleString() }}</td>
            <td class="py-2 pr-4 text-slate-200 font-mono text-xs">{{ event.action }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ event.targetType }}</td>
            <td class="py-2 pr-4 text-slate-500 text-xs font-mono truncate max-w-32">{{ event.targetId }}</td>
            <td class="py-2">
              <span
                class="px-2 py-0.5 rounded-full text-xs"
                :class="{
                  'bg-green-500/20 text-green-300': event.result === 'SUCCEEDED',
                  'bg-red-500/20 text-red-300': event.result === 'FAILED',
                  'bg-yellow-500/20 text-yellow-300': event.result === 'REJECTED',
                }"
              >
                {{ t(`audit.results.${event.result.toLowerCase()}`) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="flex items-center justify-between mt-4 text-sm text-slate-400">
        <span>{{ t('common.page') }} {{ result.page + 1 }} {{ t('common.of') }} {{ result.totalPages }}</span>
        <div class="flex gap-2">
          <button
            :disabled="!result.hasPrevious"
            class="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 hover:bg-slate-700 transition-colors"
            @click="page--; fetchEvents()"
          >{{ t('common.previous') }}</button>
          <button
            :disabled="!result.hasNext"
            class="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 hover:bg-slate-700 transition-colors"
            @click="page++; fetchEvents()"
          >{{ t('common.next') }}</button>
        </div>
      </div>
    </template>
  </div>
</template>
