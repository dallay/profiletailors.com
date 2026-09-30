<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { formatDate } from '@/lib/formatters'
import type { PagedResult } from '@/types/pagination'
import { useAdminAuthStore } from '@/stores/auth.store'
import { messages } from '@/i18n'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldLabel } from '@/components/ui/field'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ChevronLeftIcon, ChevronRightIcon, SearchIcon } from '@lucide/vue'

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
  <div class="p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-foreground">{{ t('waitlist.title') }}</h1>

    <div v-if="summary" class="flex flex-wrap gap-2 mb-4">
      <Badge variant="outline" class="admin-chip font-mono text-xs py-1 px-3">
        {{ t('waitlist.statuses.pending') }}: {{ summary.PENDING ?? 0 }}
      </Badge>
      <Badge variant="outline" class="admin-chip font-mono text-xs py-1 px-3">
        {{ t('waitlist.statuses.invited') }}: {{ summary.INVITED ?? 0 }}
      </Badge>
      <Badge variant="outline" class="admin-chip font-mono text-xs py-1 px-3">
        {{ t('waitlist.statuses.converted') }}: {{ summary.CONVERTED ?? 0 }}
      </Badge>
      <Badge variant="outline" class="admin-chip font-mono text-xs py-1 px-3">
        {{ t('waitlist.statuses.cancelled') }}: {{ summary.CANCELLED ?? 0 }}
      </Badge>
    </div>

    <div class="flex flex-wrap gap-3 mb-4">
      <div class="relative w-64">
        <SearchIcon class="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
        <Input
          v-model="search"
          type="search"
          :placeholder="t('waitlist.filters.search')"
          class="pl-8 text-sm"
          :aria-label="t('waitlist.filters.search')"
        />
      </div>
      <Input
        v-model="waitlistKeyFilter"
        type="text"
        :placeholder="t('waitlist.filters.waitlistKey')"
        class="w-40 text-sm"
        :aria-label="t('waitlist.filters.waitlistKey')"
      />
      <select
        v-model="statusFilter"
        class="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        :aria-label="t('waitlist.filters.status')"
      >
        <option value="">{{ t('waitlist.filters.all') }}</option>
        <option value="PENDING">{{ t('waitlist.statuses.pending') }}</option>
        <option value="INVITED">{{ t('waitlist.statuses.invited') }}</option>
        <option value="CONVERTED">{{ t('waitlist.statuses.converted') }}</option>
        <option value="CANCELLED">{{ t('waitlist.statuses.cancelled') }}</option>
      </select>
      <Input
        v-model="joinedFrom"
        type="date"
        :placeholder="t('waitlist.filters.joinedFrom')"
        class="w-36 text-sm"
        :aria-label="t('waitlist.filters.joinedFrom')"
      />
      <Input
        v-model="joinedTo"
        type="date"
        :placeholder="t('waitlist.filters.joinedTo')"
        class="w-36 text-sm"
        :aria-label="t('waitlist.filters.joinedTo')"
      />
      <Input
        v-model="invitedFrom"
        type="date"
        :placeholder="t('waitlist.filters.invitedFrom')"
        class="w-36 text-sm"
        :aria-label="t('waitlist.filters.invitedFrom')"
      />
      <Input
        v-model="invitedTo"
        type="date"
        :placeholder="t('waitlist.filters.invitedTo')"
        class="w-36 text-sm"
        :aria-label="t('waitlist.filters.invitedTo')"
      />
    </div>

    <div v-if="loading" class="text-muted-foreground">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-destructive">{{ error }}</div>
    <template v-else-if="result">
      <div v-if="canInvite" class="mb-3 flex items-center gap-3">
        <Button
          data-testid="bulk-invite"
          variant="outline"
          size="sm"
          :disabled="selectedIds.length === 0 || bulkInviting"
          @click="bulkInviteSelected"
        >
          {{ bulkInviting ? t('common.loading') : t('waitlist.bulkInvite', { count: selectedIds.length }) }}
        </Button>
      </div>

      <div v-if="bulkError" role="alert" class="mb-3 text-destructive">{{ bulkError }}</div>

      <Card
        v-if="bulkResults && bulkSummary"
        data-testid="bulk-results"
        role="status"
        class="mb-4 p-4"
      >
        <CardHeader class="p-0 mb-2">
          <CardTitle class="text-base font-semibold text-foreground">{{ t('waitlist.bulkResults') }}</CardTitle>
        </CardHeader>
        <CardContent class="p-0">
          <p class="mb-2 text-sm text-muted-foreground">
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
              <span class="text-foreground">{{ item.entryId }}</span>
              <span class="text-muted-foreground"> — {{ item.outcome }}</span>
              <span v-if="item.code" class="text-muted-foreground"> ({{ item.code }})</span>
            </li>
          </ul>
        </CardContent>
      </Card>

      <Table :aria-label="t('waitlist.entries')">
        <TableHeader>
          <TableRow>
            <TableHead v-if="canInvite" scope="col" class="w-10">
              <input
                data-testid="bulk-select-all"
                type="checkbox"
                :checked="result.items.length > 0 && selectedIds.length === result.items.length"
                :aria-label="t('waitlist.bulkSelectAll')"
                @change="toggleSelectAll"
              />
            </TableHead>
            <TableHead scope="col">{{ t('common.email') }}</TableHead>
            <TableHead scope="col">{{ t('common.status') }}</TableHead>
            <TableHead scope="col">{{ t('waitlist.joinedAt') }}</TableHead>
            <TableHead scope="col">{{ t('waitlist.invitedAt') }}</TableHead>
            <TableHead scope="col">{{ t('common.actions') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="entry in result.items"
            :key="entry.id"
          >
            <TableCell v-if="canInvite">
              <input
                data-testid="bulk-select"
                type="checkbox"
                :value="entry.id"
                v-model="selectedIds"
                :aria-label="t('waitlist.bulkSelect', { email: entry.email })"
              />
            </TableCell>
            <TableCell>
              <button
                type="button"
                class="text-foreground font-medium hover:underline text-left"
                @click="router.push({ name: 'waitlist-entry', params: { entryId: entry.id } })"
              >
                {{ entry.email }}
              </button>
            </TableCell>
            <TableCell>
              <StatusBadge :status="entry.status" />
            </TableCell>
            <TableCell class="text-muted-foreground">{{ formatDate(entry.joinedAt, locale) }}</TableCell>
            <TableCell class="text-muted-foreground">
              {{ formatDate(entry.invitedAt, locale) }}
            </TableCell>
            <TableCell class="flex gap-2">
              <Button
                v-if="canInvite && (entry.status === 'PENDING' || entry.status === 'INVITED')"
                variant="outline"
                size="xs"
                :disabled="invitingId === entry.id"
                @click="inviteEntry(entry)"
              >
                {{ invitingId === entry.id ? t('common.loading') : t('waitlist.invite') }}
              </Button>
              <Button
                v-if="canCancel && entry.status !== 'CONVERTED' && entry.status !== 'CANCELLED'"
                variant="destructive"
                size="xs"
                :disabled="cancellingId === entry.id"
                @click="openCancelDialog(entry)"
              >
                {{ t('waitlist.cancel') }}
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <nav class="mt-4 flex items-center justify-between gap-3" aria-label="Pagination">
        <Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="!result.hasPrevious"
          @click="page--; fetchEntries()"
        >
          <ChevronLeftIcon class="mr-1 size-3.5" />
          {{ t('common.previous') }}
        </Button>
        <span class="font-mono text-xs text-muted-foreground">
          {{ result.page + 1 }} / {{ result.totalPages }}
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="!result.hasNext"
          @click="page++; fetchEntries()"
        >
          {{ t('common.next') }}
          <ChevronRightIcon class="ml-1 size-3.5" />
        </Button>
      </nav>
    </template>

    <div
      v-if="showCancelDialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-dialog-title"
      class="fixed inset-0 z-50 flex items-center justify-center px-4"
      @keydown.esc="showCancelDialog = false"
    >
      <div class="fixed inset-0 bg-black/70" aria-hidden="true" @click="showCancelDialog = false" />
      <Card class="relative z-10 w-full max-w-md p-6">
        <h2 id="cancel-dialog-title" class="mb-2 text-lg font-semibold text-foreground">
          {{ t('waitlist.cancelConfirmTitle') }}
        </h2>
        <p class="mb-4 text-sm text-muted-foreground">
          {{ t('waitlist.cancelConfirmMessage', { email: cancelTarget?.email }) }}
        </p>
        <Field class="mb-4 space-y-1">
          <FieldLabel class="text-sm font-medium text-foreground" for="cancel-reason">
            {{ t('waitlist.cancelReason') }} <span class="text-destructive">*</span>
          </FieldLabel>
          <Input
            id="cancel-reason"
            v-model="cancelReason"
            type="text"
            required
            aria-required="true"
          />
        </Field>
        <div class="flex gap-2 justify-end">
          <Button
            variant="outline"
            @click="showCancelDialog = false"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="destructive"
            class="admin-button-danger"
            :disabled="!cancelReason.trim()"
            @click="confirmCancel"
          >
            {{ t('waitlist.cancel') }}
          </Button>
        </div>
      </Card>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, h } from 'vue'

const StatusBadge = defineComponent({
  props: { status: { type: String, required: true } },
  components: { Badge },
  setup(props) {
    const { t } = useI18n()
    return () => {
      const variant = props.status === 'CONVERTED' ? 'default' : props.status === 'CANCELLED' ? 'destructive' : 'outline'
      const statusKey = props.status.toLowerCase()
      const label =
        (statusKey in messages.en.waitlist.statuses && t(`waitlist.statuses.${statusKey}`)) ||
        props.status
      return h(
        Badge,
        { variant, class: 'font-mono text-[10px] uppercase' },
        { default: () => label },
      )
    }
  },
})

export { StatusBadge }
</script>
