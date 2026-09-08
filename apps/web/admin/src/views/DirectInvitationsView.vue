<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'
import RevokeInvitationDialog from '@/components/RevokeInvitationDialog.vue'

interface CreatedInvitation {
  id: string
  email: string
  status: string
  expiresAt: string
  version: number
}

const JSON_API_MEDIA_TYPE = 'application/vnd.api.v1+json'

type DirectInvitationTarget = 'NEW_WORKSPACE' | 'EXISTING_WORKSPACE'

const { t } = useI18n()
const authStore = useAdminAuthStore()

const canRead = authStore.hasPermission('platform.invitations.read')
const canCreate = authStore.hasPermission('platform.invitations.create')
const canRevoke = authStore.hasPermission('platform.invitations.revoke')
const canResend = authStore.hasPermission('platform.invitations.resend')

const form = reactive({
  email: '',
  target: 'EXISTING_WORKSPACE' as DirectInvitationTarget,
  workspaceId: '',
})

const submitting = ref(false)
const formError = ref<string | null>(null)
const created = ref<CreatedInvitation | null>(null)
const revokeDialogOpen = ref(false)
const revokeTarget = ref<{ id: string; email: string; expectedVersion: number } | null>(null)
const revokePending = ref(false)
const revokeError = ref<string | null>(null)
const resending = ref(false)
const resendError = ref<string | null>(null)
const lastCreatedId = ref<string | null>(null)

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const emailValid = computed(() => emailRegex.test(form.email.trim()))
const workspaceRequired = computed(() => form.target === 'EXISTING_WORKSPACE')
const workspaceValid = computed(() => !workspaceRequired.value || form.workspaceId.trim().length > 0)
const formValid = computed(() => emailValid.value && workspaceValid.value)

async function errorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { properties?: { code?: string }; code?: string; detail?: string }
    const code = body?.properties?.code ?? body?.code
    if (code) {
      const key = `errors.${code}`
      const translated = t(key)
      if (translated !== key) return translated
    }
    if (body?.detail) return body.detail
  } catch {}
  return t('common.error')
}

async function submit() {
  if (!formValid.value || submitting.value) return
  submitting.value = true
  formError.value = null
  created.value = null
  try {
    const payload = {
      email: form.email.trim(),
      target: form.target,
      workspaceId: workspaceRequired.value ? form.workspaceId.trim() : null,
    }
    const res = await authStore.request('/api/admin/invitations/direct', {
      method: 'POST',
      headers: { 'Content-Type': JSON_API_MEDIA_TYPE },
      body: JSON.stringify(payload),
    })
    if (!res.ok) {
      formError.value = await errorMessage(res)
      return
    }
    const body = (await res.json()) as {
      invitationId: string
      status: string
      expiresAt: string
      version: number
    }
    created.value = {
      id: body.invitationId,
      email: payload.email,
      status: body.status,
      expiresAt: body.expiresAt,
      version: body.version,
    }
    lastCreatedId.value = body.invitationId
    form.email = ''
    form.workspaceId = ''
    form.target = 'EXISTING_WORKSPACE'
  } catch {
    formError.value = t('common.error')
  } finally {
    submitting.value = false
  }
}

function openRevokeDialog(id: string) {
  const invitation = created.value && created.value.id === id ? created.value : null
  revokeTarget.value = {
    id,
    email: invitation?.email ?? id,
    expectedVersion: invitation?.version ?? 0,
  }
  revokeError.value = null
  revokeDialogOpen.value = true
}

async function resendInvitation() {
  if (!created.value || resending.value) return
  resending.value = true
  resendError.value = null
  try {
    const res = await authStore.request(
      `/api/admin/invitations/${created.value.id}/direct-resend`,
      {
        method: 'POST',
        headers: { 'Content-Type': JSON_API_MEDIA_TYPE },
      },
    )
    if (!res.ok) {
      resendError.value = await errorMessage(res)
      return
    }
    const body = (await res.json()) as {
      invitationId: string
      status: string
      expiresAt: string
      version: number
    }
    created.value = { ...created.value, status: body.status, expiresAt: body.expiresAt, version: body.version }
  } catch {
    resendError.value = t('common.error')
  } finally {
    resending.value = false
  }
}

async function confirmRevoke(expectedVersion: number) {
  if (!revokeTarget.value) return
  revokePending.value = true
  revokeError.value = null
  try {
    const res = await authStore.request(
      `/api/admin/invitations/${revokeTarget.value.id}/direct-revoke`,
      {
        method: 'POST',
        headers: { 'Content-Type': JSON_API_MEDIA_TYPE },
        body: JSON.stringify({ expectedVersion }),
      },
    )
    if (!res.ok) {
      revokeError.value = await errorMessage(res)
      return
    }
    revokeDialogOpen.value = false
    if (created.value && created.value.id === revokeTarget.value.id) {
      created.value = null
    }
    revokeTarget.value = null
  } catch {
    revokeError.value = t('common.error')
  } finally {
    revokePending.value = false
  }
}

function closeRevokeDialog() {
  if (revokePending.value) return
  revokeDialogOpen.value = false
  revokeError.value = null
}

function formatExpiry(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold text-text-display">
        {{ t('directInvitations.title') }}
      </h1>
      <p class="mt-1 text-sm text-text-secondary">
        {{ t('directInvitations.subtitle') }}
      </p>
    </header>

    <section
      v-if="canCreate"
      class="admin-card mb-6 p-5"
      data-testid="direct-invitation-form-card"
    >
      <h2 class="mb-4 text-lg font-medium text-text-display">
        {{ t('directInvitations.form.title') }}
      </h2>

      <form class="space-y-4" novalidate @submit.prevent="submit">
        <div>
          <label
            for="direct-invitation-email"
            class="label-mono mb-1 block text-text-secondary"
          >
            {{ t('common.email') }}
          </label>
          <input
            id="direct-invitation-email"
            v-model="form.email"
            type="email"
            autocomplete="off"
            required
            class="admin-input w-full text-sm"
            data-testid="direct-invitation-email"
            :placeholder="t('directInvitations.form.emailPlaceholder')"
            :disabled="submitting"
          >
        </div>

        <div>
          <label
            for="direct-invitation-target"
            class="label-mono mb-1 block text-text-secondary"
          >
            {{ t('directInvitations.form.target') }}
          </label>
          <select
            id="direct-invitation-target"
            v-model="form.target"
            class="admin-input w-full text-sm"
            data-testid="direct-invitation-target"
            :disabled="submitting"
          >
            <option value="EXISTING_WORKSPACE">
              {{ t('directInvitations.form.targetExisting') }}
            </option>
            <option value="NEW_WORKSPACE">
              {{ t('directInvitations.form.targetNew') }}
            </option>
          </select>
        </div>

        <div v-if="workspaceRequired">
          <label
            for="direct-invitation-workspace"
            class="label-mono mb-1 block text-text-secondary"
          >
            {{ t('directInvitations.form.workspaceId') }}
          </label>
          <input
            id="direct-invitation-workspace"
            v-model="form.workspaceId"
            type="text"
            autocomplete="off"
            required
            class="admin-input w-full text-sm"
            data-testid="direct-invitation-workspace"
            :placeholder="t('directInvitations.form.workspaceIdPlaceholder')"
            :disabled="submitting"
          >
        </div>

        <div
          v-if="formError"
          role="alert"
          class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error"
          data-testid="direct-invitation-error"
        >
          {{ formError }}
        </div>

        <div class="flex items-center gap-3">
          <button
            type="submit"
            class="admin-button-primary text-sm disabled:opacity-40"
            data-testid="direct-invitation-submit"
            :disabled="!formValid || submitting"
          >
            {{ submitting ? t('common.loading') : t('directInvitations.form.submit') }}
          </button>
          <span
            v-if="!emailValid && form.email.length > 0"
            class="text-xs text-warning"
          >
            {{ t('directInvitations.form.emailInvalid') }}
          </span>
        </div>
      </form>
    </section>

    <section
      v-if="created"
      class="admin-card p-5"
      data-testid="direct-invitation-success"
    >
      <h2 class="mb-3 text-lg font-medium text-success">
        {{ t('directInvitations.success.title') }}
      </h2>
      <dl class="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt class="label-mono mb-1 text-text-secondary">
            {{ t('directInvitations.success.id') }}
          </dt>
          <dd class="font-mono text-text-display">
            {{ created.id }}
          </dd>
        </div>
        <div>
          <dt class="label-mono mb-1 text-text-secondary">
            {{ t('common.status') }}
          </dt>
          <dd class="text-text-display">
            {{ created.status }}
          </dd>
        </div>
        <div>
          <dt class="label-mono mb-1 text-text-secondary">
            {{ t('directInvitations.success.expiresAt') }}
          </dt>
          <dd class="text-text-display">
            {{ formatExpiry(created.expiresAt) }}
          </dd>
        </div>
      </dl>
      <div class="mt-4 flex flex-wrap gap-3">
        <button
          v-if="canResend"
          type="button"
          class="admin-button-secondary text-sm disabled:opacity-40"
          data-testid="direct-invitation-resend"
          :disabled="resending"
          @click="resendInvitation"
        >
          {{ resending ? t('common.loading') : t('directInvitations.success.resend') }}
        </button>
        <button
          v-if="canRevoke"
          type="button"
          class="admin-button-danger text-sm"
          data-testid="direct-invitation-revoke"
          @click="openRevokeDialog(created.id)"
        >
          {{ t('directInvitations.success.revoke') }}
        </button>
        <span
          v-if="!canRevoke && !canResend && !canRead"
          class="text-xs text-text-secondary"
        >
          {{ t('directInvitations.success.readOnlyNotice') }}
        </span>
      </div>
      <div
        v-if="resendError"
        role="alert"
        class="mt-3 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error"
        data-testid="direct-invitation-resend-error"
      >
        {{ resendError }}
      </div>
    </section>

    <p v-if="!canRead" class="text-sm text-text-secondary">
      {{ t('auth.accessDeniedMessage') }}
    </p>

    <RevokeInvitationDialog
      :open="revokeDialogOpen"
      :invitation-id="revokeTarget?.id ?? ''"
      :email="revokeTarget?.email ?? ''"
      :expected-version="revokeTarget?.expectedVersion"
      :pending="revokePending"
      :error="revokeError"
      @close="closeRevokeDialog"
      @confirm="confirmRevoke"
    />

    <p v-if="lastCreatedId" class="sr-only" aria-live="polite">
      {{ t('directInvitations.success.createdAnnouncement') }}
    </p>
  </div>
</template>
