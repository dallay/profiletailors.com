<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAdminAuthStore } from '@/stores/auth.store'
import { messages } from '@/i18n'

const { t } = useI18n()
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

const result = ref<PagedResult<WaitlistEntry> | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const search = ref('')
const statusFilter = ref('')
const page = ref(0)
const invitingId = ref<string | null>(null)
const cancellingId = ref<string | null>(null)
const cancelReason = ref('')
const showCancelDialog = ref(false)
const cancelTarget = ref<WaitlistEntry | null>(null)

const canInvite = authStore.hasPermission('platform.waitlist.invite')
const canCancel = authStore.hasPermission('platform.waitlist.cancel')

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

    const res = await fetch(`/api/admin/waitlist-entries?${params}`)
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
    const res = await fetch(`/api/admin/waitlist-entries/${entry.id}/invitations`, { method: 'POST' })
    if (!res.ok) {
      const body = (await res.json()) as { properties?: { code?: string } }
      const code = body.properties?.code
      alert((code && code in messages.en.errors && t(`errors.${code}`)) || t('common.error'))
    } else {
      await fetchEntries()
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

async function confirmCancel() {
  if (!cancelTarget.value || !cancelReason.value.trim()) return
  cancellingId.value = cancelTarget.value.id
  showCancelDialog.value = false
  try {
    const res = await fetch(`/api/admin/waitlist-entries/${cancelTarget.value.id}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: cancelReason.value }),
    })
    if (!res.ok) {
      const body = (await res.json()) as { properties?: { code?: string } }
      const code = body.properties?.code
      alert((code && code in messages.en.errors && t(`errors.${code}`)) || t('common.error'))
    } else {
      await fetchEntries()
    }
  } finally {
    cancellingId.value = null
    cancelTarget.value = null
  }
}

watch([statusFilter, search], () => { page.value = 0; fetchEntries() })
onMounted(fetchEntries)
</script>

<template>
  <div class="p-8">
    <h1 class="text-2xl font-bold text-slate-100 mb-6">{{ t('waitlist.title') }}</h1>

    <!-- Filters -->
    <div class="flex gap-3 mb-4">
      <input
        v-model="search"
        type="search"
        :placeholder="t('waitlist.filters.search')"
        class="bg-slate-800 border border-slate-700 text-slate-200 rounded-lg px-3 py-1.5 text-sm w-64"
        :aria-label="t('waitlist.filters.search')"
      />
      <select
        v-model="statusFilter"
        class="bg-slate-800 border border-slate-700 text-slate-200 rounded-lg px-3 py-1.5 text-sm"
        :aria-label="t('waitlist.filters.status')"
      >
        <option value="">{{ t('waitlist.filters.all') }}</option>
        <option value="PENDING">{{ t('waitlist.statuses.pending') }}</option>
        <option value="INVITED">{{ t('waitlist.statuses.invited') }}</option>
        <option value="CONVERTED">{{ t('waitlist.statuses.converted') }}</option>
        <option value="CANCELLED">{{ t('waitlist.statuses.cancelled') }}</option>
      </select>
    </div>

    <div v-if="loading" class="text-slate-400">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-red-400">{{ error }}</div>
    <template v-else-if="result">
      <table class="w-full text-sm text-left border-collapse" aria-label="Waitlist entries">
        <thead>
          <tr class="border-b border-slate-800 text-slate-400 uppercase text-xs">
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
            class="border-b border-slate-800/50 hover:bg-slate-900/50"
          >
            <td class="py-2 pr-4">
              <button
                class="text-amber-400 hover:underline text-left"
                @click="router.push({ name: 'waitlist-entry', params: { entryId: entry.id } })"
              >
                {{ entry.email }}
              </button>
            </td>
            <td class="py-2 pr-4">
              <StatusBadge :status="entry.status" />
            </td>
            <td class="py-2 pr-4 text-slate-400">{{ new Date(entry.joinedAt).toLocaleDateString() }}</td>
            <td class="py-2 pr-4 text-slate-400">
              {{ entry.invitedAt ? new Date(entry.invitedAt).toLocaleDateString() : '—' }}
            </td>
            <td class="py-2 flex gap-2">
              <button
                v-if="canInvite && (entry.status === 'PENDING' || entry.status === 'INVITED')"
                :disabled="invitingId === entry.id"
                class="px-2 py-1 rounded text-xs bg-amber-500/20 text-amber-300 hover:bg-amber-500/30 disabled:opacity-50 transition-colors"
                @click="inviteEntry(entry)"
              >
                {{ invitingId === entry.id ? t('common.loading') : t('waitlist.invite') }}
              </button>
              <button
                v-if="canCancel && entry.status !== 'CONVERTED' && entry.status !== 'CANCELLED'"
                :disabled="cancellingId === entry.id"
                class="px-2 py-1 rounded text-xs bg-red-500/20 text-red-300 hover:bg-red-500/30 disabled:opacity-50 transition-colors"
                @click="openCancelDialog(entry)"
              >
                {{ t('waitlist.cancel') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Pagination -->
      <div class="flex items-center justify-between mt-4 text-sm text-slate-400">
        <span>{{ t('common.page') }} {{ result.page + 1 }} {{ t('common.of') }} {{ result.totalPages }}</span>
        <div class="flex gap-2">
          <button
            :disabled="!result.hasPrevious"
            class="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 hover:bg-slate-700 transition-colors"
            :aria-label="t('common.previous')"
            @click="page--; fetchEntries()"
          >
            {{ t('common.previous') }}
          </button>
          <button
            :disabled="!result.hasNext"
            class="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 hover:bg-slate-700 transition-colors"
            :aria-label="t('common.next')"
            @click="page++; fetchEntries()"
          >
            {{ t('common.next') }}
          </button>
        </div>
      </div>
    </template>

    <!-- Cancel dialog -->
    <div
      v-if="showCancelDialog"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="'cancel-dialog-title'"
      class="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
      @keydown.esc="showCancelDialog = false"
    >
      <div class="bg-slate-900 border border-slate-700 rounded-xl p-6 w-full max-w-md">
        <h2 id="cancel-dialog-title" class="text-lg font-semibold text-slate-100 mb-2">
          {{ t('waitlist.cancelConfirmTitle') }}
        </h2>
        <p class="text-sm text-slate-400 mb-4">
          {{ t('waitlist.cancelConfirmMessage', { email: cancelTarget?.email }) }}
        </p>
        <label class="block text-sm text-slate-300 mb-1" for="cancel-reason">
          {{ t('waitlist.cancelReason') }} <span class="text-red-400">*</span>
        </label>
        <input
          id="cancel-reason"
          v-model="cancelReason"
          type="text"
          class="w-full bg-slate-800 border border-slate-700 text-slate-200 rounded-lg px-3 py-2 text-sm mb-4"
          required
          :aria-required="true"
        />
        <div class="flex gap-2 justify-end">
          <button
            class="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white transition-colors"
            @click="showCancelDialog = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            :disabled="!cancelReason.trim()"
            class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-500 disabled:opacity-50 transition-colors"
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
  PENDING: 'bg-yellow-500/20 text-yellow-300',
  INVITED: 'bg-blue-500/20 text-blue-300',
  CONVERTED: 'bg-green-500/20 text-green-300',
  CANCELLED: 'bg-slate-500/20 text-slate-400',
}

const StatusBadge = defineComponent({
  props: { status: { type: String, required: true } },
  setup(props) {
    const { t } = useI18n()
    return () => {
      const cls = STATUS_CLASSES[props.status] ?? 'bg-slate-700 text-slate-300'
      const statusKey = props.status.toLowerCase()
      const label =
        (statusKey in messages.en.waitlist.statuses && t(`waitlist.statuses.${statusKey}`)) ||
        props.status
      return h(
        'span',
        { class: `px-2 py-0.5 rounded-full text-xs font-medium ${cls}` },
        { default: () => label },
      )
    }
  },
})

export { StatusBadge }
</script>
