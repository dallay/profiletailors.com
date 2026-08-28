<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t, locale } = useI18n()
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
const actionError = ref<string | null>(null)
const inviting = ref(false)
const revoking = ref(false)
const showRevokeDialog = ref(false)
const revokeTargetId = ref<string | null>(null)

const canInvite = authStore.hasPermission('platform.waitlist.invite')
const canRevoke = authStore.hasPermission('platform.invitations.revoke')

async function fetchEntry() {
  loading.value = true
  try {
    const res = await authStore.request(`/api/admin/waitlist-entries/${entryId}`)
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
  actionError.value = null
  try {
    const res = await authStore.request(`/api/admin/waitlist-entries/${entryId}/invitations`, { method: 'POST' })
    if (!res.ok) {
      actionError.value = await errorMessage(res)
      return
    }
    await fetchEntry()
  } catch {
    actionError.value = t('common.error')
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
  actionError.value = null
  try {
    const res = await authStore.request(`/api/admin/invitations/${revokeTargetId.value}/revoke`, { method: 'POST' })
    if (!res.ok) {
      actionError.value = await errorMessage(res)
      return
    }
    await fetchEntry()
  } catch {
    actionError.value = t('common.error')
  } finally {
    revoking.value = false
  }
}

async function errorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json()
    if (body?.properties?.code && t(`errors.${body.properties.code}`) !== `errors.${body.properties.code}`) {
      return t(`errors.${body.properties.code}`)
    }
  } catch {}
  return t('common.error')
}

onMounted(fetchEntry)
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <button
      class="mb-6 flex items-center gap-1 text-sm text-text-secondary transition-colors hover:text-text-display"
      @click="router.push({ name: 'waitlist' })"
    >
      ← {{ t('waitlist.title') }}
    </button>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-error">{{ error }}</div>
    <div v-else-if="entry">
      <h1 class="mb-1 text-2xl font-semibold text-text-display">{{ entry.email }}</h1>
      <p class="mb-6 font-mono text-sm text-text-secondary">{{ entryId }}</p>

      <div class="grid grid-cols-2 gap-4 mb-8">
        <Field :label="t('common.status')" :value="entry.status" />
        <Field :label="t('waitlist.joinedAt')" :value="new Date(entry.joinedAt).toLocaleString(locale)" />
        <Field :label="t('waitlist.invitedAt')" :value="entry.invitedAt ? new Date(entry.invitedAt).toLocaleString(locale) : '—'" />
        <Field :label="t('waitlist.source')" :value="entry.source" />
        <Field :label="t('waitlist.locale')" :value="entry.preferredLocale ?? '—'" />
        <Field :label="t('waitlist.cancelledAt')" :value="entry.cancelledAt ? new Date(entry.cancelledAt).toLocaleString(locale) : '—'" />
      </div>

      <div class="flex gap-3 mb-8">
        <button
          v-if="canInvite && (entry.status === 'PENDING' || entry.status === 'INVITED')"
          :disabled="inviting"
          class="admin-button-primary disabled:opacity-50"
          @click="invite"
        >
          {{ inviting ? t('common.loading') : t('waitlist.invite') }}
        </button>
      </div>

      <div v-if="actionError" role="alert" class="mb-4 text-sm text-error">{{ actionError }}</div>

      <h2 class="mb-3 text-lg font-semibold text-text-display">{{ t('waitlist.invitationHistory') }}</h2>
      <div v-if="!entry.invitationHistory?.length" class="text-sm text-text-secondary">{{ t('common.noData') }}</div>
      <table v-else class="admin-table w-full text-left text-sm" :aria-label="t('waitlist.invitationHistory')">
        <thead>
          <tr class="border-b border-border-subtle text-text-secondary uppercase text-xs">
            <th scope="col" class="py-2 pr-4">Status</th>
            <th scope="col" class="py-2 pr-4">Issued</th>
            <th scope="col" class="py-2 pr-4">Expires</th>
            <th scope="col" class="py-2 pr-4">Delivery</th>
            <th scope="col" class="py-2">{{ t('common.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="inv in entry.invitationHistory" :key="inv.id" class="border-b border-border-subtle">
            <td class="py-2 pr-4">{{ inv.status }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ new Date(inv.issuedAt).toLocaleString(locale) }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ new Date(inv.expiresAt).toLocaleString(locale) }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ inv.deliveryStatus }}</td>
            <td class="py-2">
              <button
                v-if="canRevoke && inv.status === 'ACTIVE'"
                class="admin-button-danger min-h-0 px-2 py-1 text-xs"
                @click="openRevokeDialog(inv.id)"
              >
                {{ t('waitlist.revoke') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="showRevokeDialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="revoke-dialog-title"
      class="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
      @keydown.esc="showRevokeDialog = false"
    >
      <div class="admin-card w-full max-w-md p-6">
        <h2 id="revoke-dialog-title" class="mb-2 text-lg font-semibold text-text-display">
          {{ t('waitlist.revokeConfirmTitle') }}
        </h2>
        <p class="mb-6 text-sm text-text-secondary">
          {{ t('waitlist.revokeConfirmMessage', { email: entry?.email }) }}
        </p>
        <div class="flex gap-2 justify-end">
          <button
            class="admin-button-secondary"
            @click="showRevokeDialog = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            class="admin-button-danger"
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
    <div class="admin-card p-4">
      <p class="label-mono mb-1 text-text-secondary">{{ label }}</p>
      <p class="text-sm text-text-body">{{ value }}</p>
    </div>
  `,
})

export { Field }
</script>
