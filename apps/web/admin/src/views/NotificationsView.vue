<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PaginationControls, Table } from '@profiletailors/vue-ui'
import { formatDateTime } from '@/lib/formatters'
import { useAdminAuthStore } from '@/stores/auth.store'

type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED'
type NotificationChannel = 'EMAIL' | 'SMS' | 'PUSH'

interface NotificationRow {
  id: string
  channel: string
  templateId: string
  recipient: string
  status: string
  errorMessage: string | null
  createdAt: string
  sentAt: string | null
  failedAt: string | null
}

interface NotificationListResponse {
  data: NotificationRow[]
  meta: {
    currentPage: number
    pageSize: number
    totalElements: number
    totalPages: number
  }
}

const STATUSES: readonly NotificationStatus[] = ['PENDING', 'SENT', 'FAILED']
const CHANNELS: readonly NotificationChannel[] = ['EMAIL', 'SMS', 'PUSH']

const { t, locale } = useI18n()
const authStore = useAdminAuthStore()

const rows = ref<NotificationRow[]>([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const retryingId = ref<string | null>(null)
const error = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const statusFilter = ref('')
const channelFilter = ref('')

const canManage = computed(() => authStore.hasPermission('platform.notifications.manage'))
const hasPrevious = computed(() => page.value > 0)
const hasNext = computed(() => page.value < totalPages.value - 1)

let activeRequest: AbortController | null = null

function isListResponse(value: unknown): value is NotificationListResponse {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as { data?: unknown; meta?: { totalPages?: unknown; currentPage?: unknown } }
  return Array.isArray(candidate.data) && typeof candidate.meta?.totalPages === 'number'
}

async function fetchNotifications(): Promise<void> {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '25' })
    if (statusFilter.value) params.set('status', statusFilter.value)
    if (channelFilter.value) params.set('channel', channelFilter.value)
    const response = await authStore.request(`/api/admin/notifications?${params}`, {
      signal: controller.signal,
    })
    if (!response.ok) {
      error.value = t('common.error')
      return
    }
    const body: unknown = await response.json()
    if (!isListResponse(body)) {
      error.value = t('common.error')
      return
    }
    rows.value = body.data
    totalPages.value = body.meta.totalPages
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function retryNotification(row: NotificationRow): Promise<void> {
  successMessage.value = null
  if (!window.confirm(t('notifications.retryConfirm', { recipient: row.recipient }))) return

  retryingId.value = row.id
  error.value = null
  try {
    const response = await authStore.request(`/api/admin/notifications/${row.id}/retry`, {
      method: 'POST',
      headers: { 'X-Idempotency-Key': crypto.randomUUID() },
    })
    if (response.status === 400) {
      error.value = t('notifications.retryNotEligible')
      return
    }
    if (!response.ok) {
      error.value = t('common.error')
      return
    }
    successMessage.value = t('notifications.retrySuccess')
    await fetchNotifications()
  } catch {
    error.value = t('common.error')
  } finally {
    retryingId.value = null
  }
}

watch([statusFilter, channelFilter], () => {
  page.value = 0
  fetchNotifications()
})
onMounted(fetchNotifications)
onBeforeUnmount(() => activeRequest?.abort())
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-text-display">{{ t('notifications.title') }}</h1>

    <div class="mb-4 flex flex-wrap gap-3">
      <select
        v-model="statusFilter"
        class="admin-input text-sm"
        :aria-label="t('notifications.filterStatus')"
      >
        <option value="">{{ t('notifications.allStatuses') }}</option>
        <option v-for="status in STATUSES" :key="status" :value="status">
          {{ t(`notifications.statuses.${status.toLowerCase()}`) }}
        </option>
      </select>
      <select
        v-model="channelFilter"
        class="admin-input text-sm"
        :aria-label="t('notifications.filterChannel')"
      >
        <option value="">{{ t('notifications.allChannels') }}</option>
        <option v-for="channel in CHANNELS" :key="channel" :value="channel">{{ channel }}</option>
      </select>
    </div>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="mb-4 text-error">{{ error }}</div>
    <template v-else>
      <p v-if="successMessage" role="status" class="mb-4 text-sm text-success">{{ successMessage }}</p>
      <p v-if="rows.length === 0" class="text-text-secondary">{{ t('notifications.empty') }}</p>
      <template v-else>
        <Table :aria-label="t('notifications.title')" class="admin-table">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary uppercase">
              <th scope="col" class="py-2 pr-4">{{ t('common.createdAt') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('notifications.channel') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('notifications.template') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('notifications.recipient') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('common.status') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('notifications.error') }}</th>
              <th v-if="canManage" scope="col" class="py-2">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.id"
              class="border-b border-border-subtle hover:bg-bg-surface"
            >
              <td class="py-2 pr-4 text-text-secondary">{{ formatDateTime(row.createdAt, locale) }}</td>
              <td class="py-2 pr-4 font-mono text-xs text-text-body">{{ row.channel }}</td>
              <td class="max-w-48 truncate py-2 pr-4 font-mono text-xs text-text-secondary">
                {{ row.templateId }}
              </td>
              <td class="py-2 pr-4 text-text-body">{{ row.recipient }}</td>
              <td class="py-2 pr-4">
                <span
                  class="status-badge"
                  :class="{
                    'bg-success/15 text-success': row.status === 'SENT',
                    'bg-error/15 text-error': row.status === 'FAILED',
                    'bg-warning/15 text-warning': row.status === 'PENDING',
                  }"
                >
                  {{ t(`notifications.statuses.${row.status.toLowerCase()}`) }}
                </span>
              </td>
              <td class="max-w-56 truncate py-2 pr-4 text-xs text-text-secondary">
                {{ row.errorMessage ?? '—' }}
              </td>
              <td v-if="canManage" class="py-2">
                <button
                  v-if="row.status === 'FAILED'"
                  type="button"
                  class="admin-button-secondary text-sm"
                  :disabled="retryingId === row.id"
                  @click="retryNotification(row)"
                >
                  {{ retryingId === row.id ? t('common.loading') : t('notifications.retry') }}
                </button>
              </td>
            </tr>
          </tbody>
        </Table>

        <PaginationControls
          :page="page"
          :total-pages="totalPages"
          :has-previous="hasPrevious"
          :has-next="hasNext"
          @previous="page--; fetchNotifications()"
          @next="page++; fetchNotifications()"
        />
      </template>
    </template>
  </div>
</template>
