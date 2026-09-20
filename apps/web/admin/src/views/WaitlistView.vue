<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { PaginationControls, Table } from '@profiletailors/vue-ui'
import { formatDate } from '@/lib/formatters'
import type { PagedResult } from '@/types/pagination'
import { useAdminAuthStore } from '@/stores/auth.store'
import { messages } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()
const authStore = useAdminAuthStore()

interface WaitlistEntry {
  id: string
  email: string
  normalizedEmail: string
  status: string
  joinedAt: string
  invitedAt: string | null
  waitlistKey: string
  source: string
  version: number
}

interface StatusSummary {
  PENDING: number
  INVITED: number
  CONVERTED: number
  CANCELLED: number
}

interface BulkEntryResult {
  entryId: string
  outcome: string
  invitationId?: string | null
  code?: string | null
}

interface BulkInviteSummary {
  requested: number
  invited: number
  skipped: number
  failed: number
}

const BULK_INVITE_MAX_ENTRIES = 50

const result = ref<PagedResult<WaitlistEntry> | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const search = ref('')
const statusFilter = ref('')
const waitlistKeyFilter = ref('')
const joinedFrom = ref('')
const joinedTo = ref('')
const invitedFrom = ref('')
const invitedTo = ref('')
const page = ref(0)
const summary = ref<StatusSummary | null>(null)
const invitingId = ref<string | null>(null)
const cancellingId = ref<string | null>(null)
const cancelReason = ref('')
const showCancelDialog = ref(false)
const cancelTarget = ref<WaitlistEntry | null>(null)
const selectedIds = ref<string[]>([])
const bulkInviting = ref(false)
const bulkResults = ref<BulkEntryResult[] | null>(null)
const bulkSummary = ref<BulkInviteSummary | null>(null)
const bulkError = ref<string | null>(null)

const canInvite = authStore.hasPermission('platform.waitlist.invite')
const canCancel = authStore.hasPermission('platform.waitlist.cancel')

async function fetchSummary() {
  try {
    const res = await authStore.request('/api/admin/waitlist-entries/summary')
    if (res.ok) summary.value = await res.json()
  } catch {
    // Summary is non-critical, fail silently
  }
}

async function fetchEntries() {
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: '25',
      sort: 'joinedAt',
      direction: 'desc',
    })
    if (statusFilter.value) params.set('status', statusFilter.value)
    if (search.value.trim()) params.set('email', search.value.trim())
    if (waitlistKeyFilter.value.trim()) params.set('waitlistKey', waitlistKeyFilter.value.trim())
    if (joinedFrom.value) params.set('joinedFrom', joinedFrom.value)
    if (joinedTo.value) params.set('joinedTo', joinedTo.value)
    if (invitedFrom.value) params.set('invitedFrom', invitedFrom.value)
    if (invitedTo.value) params.set('invitedTo', invitedTo.value)

    const res = await authStore.request(`/api/admin/waitlist-entries?${params}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    result.value = await res.json()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function inviteEntry(entry: WaitlistEntry) {
  if (!confirm(`${t('waitlist.inviteConfirmTitle')}\n${entry.email}`)) return
  invitingId.value = entry.id
  try {
    const res = await authStore.request(`/api/admin/waitlist-entries/${entry.id}/invitations`, { method: 'POST' })
    if (!res.ok) {
      const body = (await res.json()) as { properties?: { code?: string } }
      const code = body.properties?.code
      alert((code && code in messages.en.errors && t(`errors.${code}`)) || t('common.error'))
    } else {
      await Promise.all([fetchEntries(), fetchSummary()])
    }
  } finally {
    invitingId.value = null
  }
}

function openCancelDialog(entry: WaitlistEntry) {
  cancelTarget.value = entry
  cancelReason.value = ''
  showCancelDialog.value = true
}

function toggleSelectAll(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  selectedIds.value = checked && result.value ? result.value.items.map((item) => item.id) : []
}

async function bulkInviteSelected() {
  if (selectedIds.value.length === 0 || bulkInviting.value) return
  bulkError.value = null
  if (selectedIds.value.length > BULK_INVITE_MAX_ENTRIES) {
    bulkError.value = t('waitlist.bulkTooMany', { max: BULK_INVITE_MAX_ENTRIES })
    return
  }
  bulkInviting.value = true
  try {
    const res = await authStore.request('/api/admin/waitlist-entries/invitations:bulk', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entryIds: [...selectedIds.value] }),
    })
    if (!res.ok) {
      const body = (await res.json()) as { properties?: { code?: string } }
      bulkError.value = body.properties?.code ?? t('common.error')
    } else {
      const payload = (await res.json()) as { results: BulkEntryResult[]; summary: BulkInviteSummary }
      bulkResults.value = payload.results
      bulkSummary.value = payload.summary
      selectedIds.value = []
      await Promise.all([fetchEntries(), fetchSummary()])
    }
  } catch {
    bulkError.value = t('common.error')
  } finally {
    bulkInviting.value = false
  }
}

async function confirmCancel() {
  if (!cancelTarget.value || !cancelReason.value.trim()) return
  cancellingId.value = cancelTarget.value.id
  showCancelDialog.value = false
  try {
    const res = await authStore.request(`/api/admin/waitlist-entries/${cancelTarget.value.id}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: cancelReason.value, expectedVersion: cancelTarget.value.version }),
    })
    if (!res.ok) {
      const body = (await res.json()) as { properties?: { code?: string } }
      const code = body.properties?.code
      alert((code && code in messages.en.errors && t(`errors.${code}`)) || t('common.error'))
    } else {
      await Promise.all([fetchEntries(), fetchSummary()])
    }
  } finally {
    cancellingId.value = null
    cancelTarget.value = null
  }
}

watch([statusFilter, search, waitlistKeyFilter, joinedFrom, joinedTo, invitedFrom, invitedTo], () => { page.value = 0; fetchEntries() })
onMounted(() => {
  Promise.all([fetchEntries(), fetchSummary()])
})
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-text-display">{{ t('waitlist.title') }}</h1>

    <div v-if="summary" class="flex gap-2 mb-4">
      <div class="admin-chip bg-pending/10 text-pending border border-pending/30">
        {{ t('waitlist.statuses.pending') }}: {{ summary.PENDING ?? 0 }}
      </div>
      <div class="admin-chip bg-invited/10 text-invited border border-invited/30">
        {{ t('waitlist.statuses.invited') }}: {{ summary.INVITED ?? 0 }}
      </div>
      <div class="admin-chip bg-converted/10 text-converted border border-converted/30">
        {{ t('waitlist.statuses.converted') }}: {{ summary.CONVERTED ?? 0 }}
      </div>
      <div class="admin-chip bg-cancelled/10 text-cancelled border border-cancelled/30">
        {{ t('waitlist.statuses.cancelled') }}: {{ summary.CANCELLED ?? 0 }}
      </div>
    </div>

    <div class="flex flex-wrap gap-3 mb-4">
      <input
        v-model="search"
        type="search"
        :placeholder="t('waitlist.filters.search')"
        class="admin-input w-64 text-sm"
        :aria-label="t('waitlist.filters.search')"
      />
      <input
        v-model="waitlistKeyFilter"
        type="text"
        :placeholder="t('waitlist.filters.waitlistKey')"
        class="admin-input w-40 text-sm"
        :aria-label="t('waitlist.filters.waitlistKey')"
      />
      <select
        v-model="statusFilter"
        class="admin-input text-sm"
        :aria-label="t('waitlist.filters.status')"
      >
        <option value="">{{ t('waitlist.filters.all') }}</option>
        <option value="PENDING">{{ t('waitlist.statuses.pending') }}</option>
        <option value="INVITED">{{ t('waitlist.statuses.invited') }}</option>
        <option value="CONVERTED">{{ t('waitlist.statuses.converted') }}</option>
        <option value="CANCELLED">{{ t('waitlist.statuses.cancelled') }}</option>
      </select>
      <input
        v-model="joinedFrom"
        type="date"
        :placeholder="t('waitlist.filters.joinedFrom')"
        class="admin-input w-36 text-sm"
        :aria-label="t('waitlist.filters.joinedFrom')"
      />
      <input
        v-model="joinedTo"
        type="date"
        :placeholder="t('waitlist.filters.joinedTo')"
        class="admin-input w-36 text-sm"
        :aria-label="t('waitlist.filters.joinedTo')"
      />
      <input
        v-model="invitedFrom"
        type="date"
        :placeholder="t('waitlist.filters.invitedFrom')"
        class="admin-input w-36 text-sm"
        :aria-label="t('waitlist.filters.invitedFrom')"
      />
      <input
        v-model="invitedTo"
        type="date"
        :placeholder="t('waitlist.filters.invitedTo')"
        class="admin-input w-36 text-sm"
        :aria-label="t('waitlist.filters.invitedTo')"
      />
    </div>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-error">{{ error }}</div>
    <template v-else-if="result">
      <div v-if="canInvite" class="mb-3 flex items-center gap-3">
        <button
          data-testid="bulk-invite"
          :disabled="selectedIds.length === 0 || bulkInviting"
          class="admin-button-secondary text-sm disabled:opacity-50"
          @click="bulkInviteSelected"
        >
          {{ bulkInviting ? t('common.loading') : t('waitlist.bulkInvite', { count: selectedIds.length }) }}
        </button>
      </div>

      <div v-if="bulkError" role="alert" class="mb-3 text-error">{{ bulkError }}</div>

      <div
        v-if="bulkResults && bulkSummary"
        data-testid="bulk-results"
        role="status"
        class="admin-card mb-4 p-4"
      >
        <h2 class="mb-2 text-base font-semibold text-text-display">{{ t('waitlist.bulkResults') }}</h2>
        <p class="mb-2 text-sm text-text-secondary">
          {{
            t('waitlist.bulkSummary', {
              invited: bulkSummary.invited,
              skipped: bulkSummary.skipped,
              failed: bulkSummary.failed,
            })
          }}
        </p>
        <ul class="text-sm">
          <li v-for="item in bulkResults" :key="item.entryId" class="py-1">
            <span class="text-text-display">{{ item.entryId }}</span>
            <span class="text-text-secondary"> — {{ item.outcome }}</span>
            <span v-if="item.code" class="text-text-secondary"> ({{ item.code }})</span>
          </li>
        </ul>
      </div>

      <Table :aria-label="t('waitlist.entries')" class="admin-table">
        <thead>
          <tr class="border-b border-border-subtle text-text-secondary uppercase text-xs">
            <th v-if="canInvite" scope="col" class="py-2 pr-4">
              <input
                data-testid="bulk-select-all"
                type="checkbox"
                :checked="result.items.length > 0 && selectedIds.length === result.items.length"
                :aria-label="t('waitlist.bulkSelectAll')"
                @change="toggleSelectAll"
              />
            </th>
            <th scope="col" class="py-2 pr-4">{{ t('common.email') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('common.status') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('waitlist.joinedAt') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('waitlist.invitedAt') }}</th>
            <th scope="col" class="py-2">{{ t('common.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in result.items"
            :key="entry.id"
            class="border-b border-border-subtle hover:bg-bg-surface"
          >
            <td v-if="canInvite" class="py-2 pr-4">
              <input
                data-testid="bulk-select"
                type="checkbox"
                :value="entry.id"
                v-model="selectedIds"
                :aria-label="t('waitlist.bulkSelect', { email: entry.email })"
              />
            </td>
            <td class="py-2 pr-4">
              <button
                class="text-text-display hover:underline text-left"
                @click="router.push({ name: 'waitlist-entry', params: { entryId: entry.id } })"
              >
                {{ entry.email }}
              </button>
            </td>
            <td class="py-2 pr-4">
              <StatusBadge :status="entry.status" />
            </td>
            <td class="py-2 pr-4 text-text-secondary">{{ formatDate(entry.joinedAt, locale) }}</td>
            <td class="py-2 pr-4 text-text-secondary">
              {{ formatDate(entry.invitedAt, locale) }}
            </td>
            <td class="py-2 flex gap-2">
              <button
                v-if="canInvite && (entry.status === 'PENDING' || entry.status === 'INVITED')"
                :disabled="invitingId === entry.id"
                class="admin-button-secondary min-h-0 px-2 py-1 text-xs disabled:opacity-50"
                @click="inviteEntry(entry)"
              >
                {{ invitingId === entry.id ? t('common.loading') : t('waitlist.invite') }}
              </button>
              <button
                v-if="canCancel && entry.status !== 'CONVERTED' && entry.status !== 'CANCELLED'"
                :disabled="cancellingId === entry.id"
                class="admin-button-danger min-h-0 px-2 py-1 text-xs disabled:opacity-50"
                @click="openCancelDialog(entry)"
              >
                {{ t('waitlist.cancel') }}
              </button>
            </td>
          </tr>
        </tbody>
      </Table>

      <PaginationControls
        :page="result.page"
        :total-pages="result.totalPages"
        :has-previous="result.hasPrevious"
        :has-next="result.hasNext"
        @previous="page--; fetchEntries()"
        @next="page++; fetchEntries()"
      />
    </template>

    <div
      v-if="showCancelDialog"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="'cancel-dialog-title'"
      class="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
      @keydown.esc="showCancelDialog = false"
    >
      <div class="admin-card w-full max-w-md p-6">
        <h2 id="cancel-dialog-title" class="mb-2 text-lg font-semibold text-text-display">
          {{ t('waitlist.cancelConfirmTitle') }}
        </h2>
        <p class="mb-4 text-sm text-text-secondary">
          {{ t('waitlist.cancelConfirmMessage', { email: cancelTarget?.email }) }}
        </p>
        <label class="mb-1 block text-sm text-text-body" for="cancel-reason">
          {{ t('waitlist.cancelReason') }} <span class="text-error">*</span>
        </label>
        <input
          id="cancel-reason"
          v-model="cancelReason"
          type="text"
          class="admin-input mb-4 w-full text-sm"
          required
          :aria-required="true"
        />
        <div class="flex gap-2 justify-end">
          <button
            class="admin-button-secondary"
            @click="showCancelDialog = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            :disabled="!cancelReason.trim()"
            class="admin-button-danger disabled:opacity-50"
            @click="confirmCancel"
          >
            {{ t('waitlist.cancel') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, h } from 'vue'

const STATUS_CLASSES: Record<string, string> = {
  PENDING: 'bg-warning/15 text-warning',
  INVITED: 'bg-text-secondary/15 text-text-secondary',
  CONVERTED: 'bg-success/15 text-success',
  CANCELLED: 'bg-text-secondary/15 text-text-secondary',
}

const StatusBadge = defineComponent({
  props: { status: { type: String, required: true } },
  setup(props) {
const { t, locale } = useI18n()
    return () => {
      const cls = STATUS_CLASSES[props.status] ?? 'bg-text-secondary/15 text-text-secondary'
      const statusKey = props.status.toLowerCase()
      const label =
        (statusKey in messages.en.waitlist.statuses && t(`waitlist.statuses.${statusKey}`)) ||
        props.status
      return h(
        'span',
        { class: `status-badge ${cls}` },
        { default: () => label },
      )
    }
  },
})

export { StatusBadge }
</script>
