<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAdminAuthStore()

const principalId = route.params.principalId as string

interface AdminUserDetail {
  principalId: string
  email: string | null
  displayIdentity: string | null
  principalType: string
  createdAt: string
  lastAuthenticatedAt: string | null
  authenticationMethods: string[]
  workspaceMemberships: AdminWorkspaceMembership[]
  platformRoles: string[]
  status: string
  version: number
}

interface AdminWorkspaceMembership {
  workspaceId: string
  workspaceName: string
  membershipStatus: string
  workspaceRoles: string[]
  joinedAt: string
}

const user = ref<AdminUserDetail | null>(null)
const workspaces = ref<AdminWorkspaceMembership[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const actionLoading = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

async function deactivateUser() {
  if (!user.value) return
  if (!confirm(t('users.deactivateConfirm'))) return
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    const res = await authStore.request(`/api/admin/users/${principalId}/deactivate`, {
      method: 'PATCH',
      body: JSON.stringify({ expectedVersion: user.value.version }),
    })
    if (!res.ok) throw new Error()
    actionSuccess.value = t('users.deactivateSuccess')
    await fetchUser()
  } catch {
    actionError.value = t('users.deactivateError')
  } finally {
    actionLoading.value = false
  }
}

async function reactivateUser() {
  if (!user.value) return
  if (!confirm(t('users.reactivateConfirm'))) return
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    const res = await authStore.request(`/api/admin/users/${principalId}/reactivate`, {
      method: 'PATCH',
      body: JSON.stringify({ expectedVersion: user.value.version }),
    })
    if (!res.ok) throw new Error()
    actionSuccess.value = t('users.reactivateSuccess')
    await fetchUser()
  } catch {
    actionError.value = t('users.reactivateError')
  } finally {
    actionLoading.value = false
  }
}

async function fetchUser() {
  loading.value = true
  try {
    const [userRes, wsRes] = await Promise.all([
      authStore.request(`/api/admin/users/${principalId}`),
      authStore.request(`/api/admin/users/${principalId}/workspaces`),
    ])
    if (userRes.ok) user.value = await userRes.json()
    else error.value = t('common.error')
    if (wsRes.ok) workspaces.value = await wsRes.json()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(fetchUser)
</script>
<template>
  <div class="admin-page p-5 sm:p-8">
    <button
      class="mb-6 flex items-center gap-1 text-sm text-text-secondary transition-colors hover:text-text-display"
      @click="router.push({ name: 'users' })"
    >
      ← {{ t('users.title') }}
    </button>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-error">{{ error }}</div>
    <div v-else-if="user">
      <h1 class="mb-1 text-2xl font-semibold text-text-display">{{ user.email }}</h1>
      <p class="mb-6 font-mono text-xs text-text-secondary">{{ principalId }}</p>

      <div class="grid grid-cols-2 gap-4 mb-8">
        <Field :label="t('users.displayName')" :value="user.displayIdentity ?? '—'" />
        <Field :label="t('users.principalType')" :value="user.principalType" />
        <Field :label="t('common.createdAt')" :value="new Date(user.createdAt).toLocaleString(locale)" />
        <Field :label="t('users.lastAuthenticated')" :value="user.lastAuthenticatedAt ? new Date(user.lastAuthenticatedAt).toLocaleString(locale) : '—'" />
        <Field :label="t('users.platformRoles')" :value="user.platformRoles?.join(', ') || '—'" />
        <div>
          <p class="mb-1 text-xs text-text-secondary uppercase">{{ t('users.status') }}</p>
          <span
            class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
            :class="{
              'bg-green-900/40 text-green-400': user.status === 'ACTIVE',
              'bg-red-900/40 text-red-400': user.status === 'DEACTIVATED',
              'bg-yellow-900/40 text-yellow-400': user.status === 'SUSPENDED',
            }"
          >{{ t(`users.${user.status.toLowerCase()}`) }}</span>
        </div>
      </div>

      <div class="mb-6 flex items-center gap-3">
        <button
          v-if="user.status === 'ACTIVE' || user.status === 'SUSPENDED'"
          class="admin-button-danger min-h-0 px-2 py-1 text-xs disabled:opacity-50"
          :disabled="actionLoading"
          @click="deactivateUser"
        >
          {{ t('users.deactivate') }}
        </button>
        <button
          v-if="user.status === 'DEACTIVATED'"
          class="admin-button-primary min-h-0 px-2 py-1 text-xs disabled:opacity-50"
          :disabled="actionLoading"
          @click="reactivateUser"
        >
          {{ t('users.reactivate') }}
        </button>
        <div v-if="actionSuccess" role="status" class="text-sm text-green-400">{{ actionSuccess }}</div>
        <div v-if="actionError" role="alert" class="text-sm text-error">{{ actionError }}</div>
      </div>

      <h2 class="mb-3 text-lg font-semibold text-text-display">{{ t('users.workspaces') }}</h2>
      <div v-if="!workspaces.length" class="text-sm text-text-secondary">{{ t('common.noData') }}</div>
      <table v-else class="admin-table w-full text-left text-sm" :aria-label="t('users.workspaceMemberships')">
        <thead>
          <tr class="border-b border-border-subtle text-text-secondary uppercase text-xs">
            <th scope="col" class="py-2 pr-4">Workspace</th>
            <th scope="col" class="py-2 pr-4">{{ t('common.status') }}</th>
            <th scope="col" class="py-2 pr-4">Roles</th>
            <th scope="col" class="py-2">Joined</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ws in workspaces" :key="ws.workspaceId" class="border-b border-border-subtle">
            <td class="py-2 pr-4 text-text-body">{{ ws.workspaceName }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ ws.membershipStatus }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ ws.workspaceRoles?.join(', ') || '—' }}</td>
            <td class="py-2 text-text-secondary">{{ new Date(ws.joinedAt).toLocaleDateString(locale) }}</td>
          </tr>
        </tbody>
      </table>
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
