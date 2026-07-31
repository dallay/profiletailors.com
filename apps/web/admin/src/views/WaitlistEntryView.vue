<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAdminAuthStore()

const entryId = route.params.entryId as string

interface InvitationSummary {
  id: string
  status: string
  issuedAt: string
  expiresAt: string
  deliveryStatus: string
}

interface WaitlistEntryDetail {
  email: string
  status: string
  joinedAt: string
  invitedAt: string | null
  cancelledAt: string | null
  source: string
  preferredLocale: string | null
  invitationHistory: InvitationSummary[]
}

const entry = ref<WaitlistEntryDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const inviting = ref(false)
const revoking = ref(false)
const showRevokeDialog = ref(false)
const revokeTargetId = ref<string | null>(null)

const canInvite = authStore.hasPermission('platform.waitlist.invite')
const canRevoke = authStore.hasPermission('platform.invitations.revoke')

async function fetchEntry() {
  loading.value = true
  try {
    const res = await fetch(`/api/admin/waitlist-entries/${entryId}`)
    if (res.ok) entry.value = await res.json()
    else error.value = t('common.error')
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function invite() {
  inviting.value = true
  try {
    await fetch(`/api/admin/waitlist-entries/${entryId}/invitations`, { method: 'POST' })
    await fetchEntry()
  } finally {
    inviting.value = false
  }
}

function openRevokeDialog(invitationId: string) {
  revokeTargetId.value = invitationId
  showRevokeDialog.value = true
}

async function confirmRevoke() {
  if (!revokeTargetId.value) return
  revoking.value = true
  showRevokeDialog.value = false
  try {
    await fetch(`/api/admin/invitations/${revokeTargetId.value}/revoke`, { method: 'POST' })
    await fetchEntry()
  } finally {
    revoking.value = false
  }
}

onMounted(fetchEntry)
</script>

<template>
  <div class="p-8">
    <button
      class="text-sm text-slate-400 hover:text-white mb-6 flex items-center gap-1 transition-colors"
      @click="router.push({ name: 'waitlist' })"
    >
      ← {{ t('waitlist.title') }}
    </button>

    <div v-if="loading" class="text-slate-400">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-red-400">{{ error }}</div>
    <div v-else-if="entry">
      <h1 class="text-2xl font-bold text-slate-100 mb-1">{{ entry.email }}</h1>
      <p class="text-sm text-slate-400 mb-6">{{ entryId }}</p>

      <div class="grid grid-cols-2 gap-4 mb-8">
        <Field :label="t('common.status')" :value="entry.status" />
        <Field :label="t('waitlist.joinedAt')" :value="new Date(entry.joinedAt).toLocaleString()" />
        <Field :label="t('waitlist.invitedAt')" :value="entry.invitedAt ? new Date(entry.invitedAt).toLocaleString() : '—'" />
        <Field :label="t('waitlist.source')" :value="entry.source" />
        <Field :label="t('waitlist.locale')" :value="entry.preferredLocale ?? '—'" />
        <Field :label="t('waitlist.cancelledAt')" :value="entry.cancelledAt ? new Date(entry.cancelledAt).toLocaleString() : '—'" />
      </div>

      <!-- Actions -->
      <div class="flex gap-3 mb-8">
        <button
          v-if="canInvite && (entry.status === 'PENDING' || entry.status === 'INVITED')"
          :disabled="inviting"
          class="px-4 py-2 rounded-lg bg-amber-500 text-slate-950 hover:bg-amber-400 disabled:opacity-50 font-medium text-sm transition-colors"
          @click="invite"
        >
          {{ inviting ? t('common.loading') : t('waitlist.invite') }}
        </button>
      </div>

      <!-- Invitation history -->
      <h2 class="text-lg font-semibold text-slate-100 mb-3">Invitation History</h2>
      <div v-if="!entry.invitationHistory?.length" class="text-slate-400 text-sm">{{ t('common.noData') }}</div>
      <table v-else class="w-full text-sm text-left border-collapse" aria-label="Invitation history">
        <thead>
          <tr class="border-b border-slate-800 text-slate-400 uppercase text-xs">
            <th scope="col" class="py-2 pr-4">Status</th>
            <th scope="col" class="py-2 pr-4">Issued</th>
            <th scope="col" class="py-2 pr-4">Expires</th>
            <th scope="col" class="py-2 pr-4">Delivery</th>
            <th scope="col" class="py-2">{{ t('common.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="inv in entry.invitationHistory" :key="inv.id" class="border-b border-slate-800/50">
            <td class="py-2 pr-4">{{ inv.status }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ new Date(inv.issuedAt).toLocaleString() }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ new Date(inv.expiresAt).toLocaleString() }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ inv.deliveryStatus }}</td>
            <td class="py-2">
              <button
                v-if="canRevoke && inv.status === 'ACTIVE'"
                class="px-2 py-1 rounded text-xs bg-red-500/20 text-red-300 hover:bg-red-500/30 transition-colors"
                @click="openRevokeDialog(inv.id)"
              >
                {{ t('waitlist.revoke') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Revoke dialog -->
    <div
      v-if="showRevokeDialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="revoke-dialog-title"
      class="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
      @keydown.esc="showRevokeDialog = false"
    >
      <div class="bg-slate-900 border border-slate-700 rounded-xl p-6 w-full max-w-md">
        <h2 id="revoke-dialog-title" class="text-lg font-semibold text-slate-100 mb-2">
          {{ t('waitlist.revokeConfirmTitle') }}
        </h2>
        <p class="text-sm text-slate-400 mb-6">
          {{ t('waitlist.revokeConfirmMessage', { email: entry?.email }) }}
        </p>
        <div class="flex gap-2 justify-end">
          <button
            class="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white transition-colors"
            @click="showRevokeDialog = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-500 transition-colors"
            @click="confirmRevoke"
          >
            {{ t('waitlist.revoke') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

const Field = defineComponent({
  props: { label: { type: String, required: true }, value: { type: String, required: true } },
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-lg p-4">
      <p class="text-xs text-slate-400 uppercase tracking-wide mb-1">{{ label }}</p>
      <p class="text-sm text-slate-200">{{ value }}</p>
    </div>
  `,
})

export { Field }
</script>
