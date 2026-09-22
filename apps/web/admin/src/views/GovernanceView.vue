<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { PaginationControls, Table } from '@profiletailors/vue-ui'
import { formatDateTime } from '@/lib/formatters'
import type { PagedResult } from '@/types/pagination'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAdminAuthStore()

interface TakedownReport {
  reportId: string
  workspaceId: string
  assetId: string
  reportedById: string
  reason: string
  status: string
  reporterEmail: string
  mediaReferenceUrl: string
  createdAt: string
  updatedAt: string
  reviewedById?: string | null
  reviewedAt?: string | null
  rejectionReason?: string | null
  assetStatus?: string | null
}

const result = ref<PagedResult<TakedownReport> | null>(null)
const report = ref<TakedownReport | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const statusFilter = ref('')
const workspaceFilter = ref('')
const page = ref(0)
const rejecting = ref(false)

let activeRequest: AbortController | null = null

const isDetailView = computed(() => !!route.params.reportId)
const canMutate = computed(() => authStore.hasPermission('platform.governance.manage'))

const statusOptions = [
  { value: '', label: computed(() => t('governance.allStatuses')) },
  { value: 'REPORTED', label: computed(() => t('governance.statuses.REPORTED')) },
  { value: 'APPROVED', label: computed(() => t('governance.statuses.APPROVED')) },
  { value: 'DISMISSED', label: computed(() => t('governance.statuses.DISMISSED')) },
]

async function fetchReports() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '20' })
    if (statusFilter.value) params.set('status', statusFilter.value)
    if (workspaceFilter.value) params.set('workspaceId', workspaceFilter.value)
    const res = await authStore.request(`/api/admin/takedown-reports?${params}`, { signal: controller.signal })
    if (!res.ok) throw new Error()
    result.value = await res.json()
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function fetchReportDetail(reportId: string) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = null
  try {
    const res = await authStore.request(`/api/admin/takedown-reports/${reportId}`, { signal: controller.signal })
    if (!res.ok) throw new Error()
    report.value = await res.json()
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function approveReport(reportId: string) {
  if (!canMutate.value) return
  if (!window.confirm(t('governance.approveConfirm'))) return
  const idempotencyKey = `ui-${reportId}-${Date.now()}`
  try {
    const res = await authStore.request(`/api/admin/takedown-reports/${reportId}/approve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
    })
    if (!res.ok) throw new Error()
    report.value = await res.json()
  } catch {
    error.value = t('common.error')
  }
}

async function rejectReport(reportId: string, reason: string) {
  if (!canMutate.value) return
  rejecting.value = true
  const idempotencyKey = `ui-reject-${reportId}-${Date.now()}`
  try {
    const res = await authStore.request(`/api/admin/takedown-reports/${reportId}/reject`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ rejectionReason: reason }),
    })
    if (!res.ok) throw new Error()
    report.value = await res.json()
  } catch {
    error.value = t('common.error')
  } finally {
    rejecting.value = false
  }
}

function openRejectDialog(reportId: string) {
  const reason = window.prompt(t('governance.rejectConfirm'))
  if (reason?.trim()) {
    rejectReport(reportId, reason.trim())
  }
}

function navigateToDetail(r: TakedownReport) {
  router.push({ name: 'governance-detail', params: { reportId: r.reportId } })
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    REPORTED: t('governance.statuses.REPORTED'),
    APPROVED: t('governance.statuses.APPROVED'),
    DISMISSED: t('governance.statuses.DISMISSED'),
  }
  return map[status] ?? status
}

function getAssetStatusLabel(status: string | null | undefined): string {
  if (!status) return t('governance.noAssetStatus')
  const map: Record<string, string> = {
    ACTIVE: t('governance.assetStatus.active'),
    SUSPENDED: t('governance.assetStatus.suspended'),
    DELETED: t('governance.assetStatus.deleted'),
  }
  return map[status] ?? status
}

watch([statusFilter, workspaceFilter], () => { page.value = 0; fetchReports() })

onMounted(() => {
  if (isDetailView.value) {
    fetchReportDetail(route.params.reportId as string)
  } else {
    fetchReports()
  }
})
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-text-display">{{ t('governance.title') }}</h1>

    <div v-if="loading && !report && !result" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error && !report && !result" role="alert" class="text-red-500">{{ error }}</div>

    <template v-else-if="isDetailView && report">
      <div class="bg-surface-elevated rounded-lg border border-border-subtle p-6">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.reportId') }}</span>
            <p class="font-mono text-sm">{{ report.reportId }}</p>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.workspace') }}</span>
            <p class="font-mono text-sm">{{ report.workspaceId }}</p>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.assetId') }}</span>
            <p class="font-mono text-sm">{{ report.assetId }}</p>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.status') }}</span>
            <p class="font-medium">{{ getStatusLabel(report.status) }}</p>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.reportedAt') }}</span>
            <p class="text-sm">{{ formatDateTime(report.createdAt, locale) }}</p>
          </div>
          <div v-if="report.reviewedAt">
            <span class="text-sm text-text-secondary">{{ t('governance.reviewedAt') }}</span>
            <p class="text-sm">{{ formatDateTime(report.reviewedAt, locale) }}</p>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.reason') }}</span>
            <p class="text-sm">{{ report.reason }}</p>
          </div>
          <div v-if="report.rejectionReason">
            <span class="text-sm text-text-secondary">{{ t('governance.rejectionReason') }}</span>
            <p class="text-sm">{{ report.rejectionReason }}</p>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.reporterEmail') }}</span>
            <p class="text-sm">{{ report.reporterEmail }}</p>
          </div>
          <div v-if="report.mediaReferenceUrl">
            <span class="text-sm text-text-secondary">{{ t('governance.mediaUrl') }}</span>
            <a :href="report.mediaReferenceUrl" target="_blank" class="text-sm text-accent-link hover:underline">{{ report.mediaReferenceUrl }}</a>
          </div>
          <div>
            <span class="text-sm text-text-secondary">{{ t('governance.assetStatus') }}</span>
            <p class="text-sm">{{ getAssetStatusLabel(report.assetStatus) }}</p>
          </div>
        </div>

        <div v-if="canMutate && report.status === 'REPORTED'" class="mt-6 flex gap-3 border-t border-border-subtle pt-4">
          <button
            class="rounded bg-accent-primary px-4 py-1.5 text-sm font-medium text-white hover:opacity-90"
            @click="approveReport(report.reportId)"
          >
            {{ t('governance.approve') }}
          </button>
          <button
            class="rounded border border-border-subtle px-4 py-1.5 text-sm hover:bg-surface-hover"
            :disabled="rejecting"
            @click="openRejectDialog(report.reportId)"
          >
            {{ t('governance.reject') }}
          </button>
        </div>
        <div v-if="error" class="mt-4 text-sm text-red-500">{{ error }}</div>
      </div>

      <div class="mt-4">
        <button
          class="rounded px-3 py-1.5 text-sm hover:bg-surface-hover"
          @click="router.push({ name: 'governance' })"
        >
          {{ t('common.back') }}
        </button>
      </div>
    </template>

    <template v-else-if="result">
      <div class="mb-4 flex flex-wrap gap-3">
        <select
          v-model="statusFilter"
          class="rounded border border-border-subtle bg-surface-elevated px-3 py-1.5 text-sm"
        >
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
            {{ opt.label.value }}
          </option>
        </select>
        <input
          v-model="workspaceFilter"
          type="text"
          :placeholder="t('governance.workspace')"
          class="rounded border border-border-subtle bg-surface-elevated px-3 py-1.5 text-sm"
        />
      </div>

      <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
      <div v-else-if="error" role="alert" class="text-red-500">{{ error }}</div>
      <template v-else>
        <Table :aria-label="t('governance.title')" class="admin-table">
          <thead>
            <tr class="border-b border-border-subtle text-text-secondary uppercase text-xs">
              <th scope="col" class="py-2 pr-4">{{ t('governance.reportId') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('governance.workspace') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('governance.status') }}</th>
              <th scope="col" class="py-2 pr-4">{{ t('governance.reportedAt') }}</th>
              <th scope="col" class="py-2">{{ t('governance.reviewedAt') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in result.items"
              :key="r.reportId"
              class="cursor-pointer border-b border-border-subtle hover:bg-surface-hover"
              @click="navigateToDetail(r)"
            >
              <td class="py-2 pr-4 font-mono text-sm">{{ r.reportId }}</td>
              <td class="py-2 pr-4 font-mono text-sm">{{ r.workspaceId }}</td>
              <td class="py-2 pr-4 text-sm">{{ getStatusLabel(r.status) }}</td>
              <td class="py-2 pr-4 text-sm">{{ formatDateTime(r.createdAt, locale) }}</td>
              <td class="py-2 text-sm">{{ r.reviewedAt ? formatDateTime(r.reviewedAt, locale) : '—' }}</td>
            </tr>
          </tbody>
        </Table>

        <PaginationControls
          v-if="result"
          :page="result.page"
          :total-pages="result.totalPages"
          :has-previous="result.hasPrevious"
          :has-next="result.hasNext"
          @previous="page--; fetchReports()"
          @next="page++; fetchReports()"
        />
      </template>
    </template>
  </div>
</template>
