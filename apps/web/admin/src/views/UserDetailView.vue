<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

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

async function fetchUser() {
  loading.value = true
  try {
    const [userRes, wsRes] = await Promise.all([
      fetch(`/api/admin/users/${principalId}`),
      fetch(`/api/admin/users/${principalId}/workspaces`),
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
  <div class="p-8">
    <button
      class="text-sm text-slate-400 hover:text-white mb-6 flex items-center gap-1 transition-colors"
      @click="router.push({ name: 'users' })"
    >
      ← {{ t('users.title') }}
    </button>

    <div v-if="loading" class="text-slate-400">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-red-400">{{ error }}</div>
    <div v-else-if="user">
      <h1 class="text-2xl font-bold text-slate-100 mb-1">{{ user.email }}</h1>
      <p class="text-xs text-slate-500 mb-6">{{ principalId }}</p>

      <div class="grid grid-cols-2 gap-4 mb-8">
        <Field :label="t('users.displayName')" :value="user.displayIdentity ?? '—'" />
        <Field :label="t('users.principalType')" :value="user.principalType" />
        <Field :label="t('common.createdAt')" :value="new Date(user.createdAt).toLocaleString()" />
        <Field :label="t('users.lastAuthenticated')" :value="user.lastAuthenticatedAt ? new Date(user.lastAuthenticatedAt).toLocaleString() : '—'" />
        <Field :label="t('users.platformRoles')" :value="user.platformRoles?.join(', ') || '—'" />
      </div>

      <h2 class="text-lg font-semibold text-slate-100 mb-3">{{ t('users.workspaces') }}</h2>
      <div v-if="!workspaces.length" class="text-slate-400 text-sm">{{ t('common.noData') }}</div>
      <table v-else class="w-full text-sm text-left border-collapse" aria-label="Workspace memberships">
        <thead>
          <tr class="border-b border-slate-800 text-slate-400 uppercase text-xs">
            <th scope="col" class="py-2 pr-4">Workspace</th>
            <th scope="col" class="py-2 pr-4">{{ t('common.status') }}</th>
            <th scope="col" class="py-2 pr-4">Roles</th>
            <th scope="col" class="py-2">Joined</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ws in workspaces" :key="ws.workspaceId" class="border-b border-slate-800/50">
            <td class="py-2 pr-4 text-slate-200">{{ ws.workspaceName }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ ws.membershipStatus }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ ws.workspaceRoles?.join(', ') || '—' }}</td>
            <td class="py-2 text-slate-400">{{ new Date(ws.joinedAt).toLocaleDateString() }}</td>
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
    <div class="bg-slate-900 border border-slate-800 rounded-lg p-4">
      <p class="text-xs text-slate-400 uppercase tracking-wide mb-1">{{ label }}</p>
      <p class="text-sm text-slate-200">{{ value }}</p>
    </div>
  `,
})

export { Field }
</script>

