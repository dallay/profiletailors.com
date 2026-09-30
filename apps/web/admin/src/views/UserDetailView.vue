<script setup lang="ts">
import { ref, onMounted, defineComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { formatDate, formatDateTime } from '@/lib/formatters'
import { useAdminAuthStore } from '@/stores/auth.store'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ArrowLeftIcon } from '@lucide/vue'

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
  accountState: 'ACTIVE' | 'DISABLED'
  emailStatus: string | null
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
const mutationError = ref<string | null>(null)

function mutationKey(operation: string): string {
  return `admin-user-${operation}-${principalId}-${crypto.randomUUID()}`
}

async function runMutation(operation: 'disable' | 'enable' | 'sessions/revoke'): Promise<void> {
  mutationError.value = null
  try {
    const response = await authStore.request(`/api/admin/users/${principalId}/${operation}`, {
      method: 'POST',
      headers: { 'Idempotency-Key': mutationKey(operation) },
    })
    if (!response.ok) {
      mutationError.value = t('common.error')
      return
    }
    await fetchUser()
  } catch {
    mutationError.value = t('common.error')
  }
}

async function disableUser(): Promise<void> {
  if (window.confirm(t('users.disableConfirm'))) await runMutation('disable')
}

async function enableUser(): Promise<void> {
  if (window.confirm(t('users.enableConfirm'))) await runMutation('enable')
}

async function revokeSessions(): Promise<void> {
  if (window.confirm(t('users.revokeSessionsConfirm'))) await runMutation('sessions/revoke')
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
  <div class="p-5 sm:p-8">
    <Button
      variant="ghost"
      size="sm"
      class="mb-6 flex items-center gap-1.5 text-muted-foreground hover:text-foreground"
      @click="router.push({ name: 'users' })"
    >
      <ArrowLeftIcon class="size-4" />
      {{ t('users.title') }}
    </Button>

    <div v-if="loading" class="text-muted-foreground">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-destructive">{{ error }}</div>
    <div v-else-if="user">
      <h1 class="mb-1 text-2xl font-semibold text-foreground">{{ user.email }}</h1>
      <p class="mb-6 font-mono text-xs text-muted-foreground">{{ principalId }}</p>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        <Field :label="t('users.displayName')" :value="user.displayIdentity ?? '—'" />
        <Field :label="t('users.principalType')" :value="user.principalType" />
        <Field :label="t('users.accountState')" :value="user.accountState" />
        <Field :label="t('users.verificationState')" :value="user.emailStatus ?? '—'" />
        <Field :label="t('common.createdAt')" :value="formatDateTime(user.createdAt, locale)" />
        <Field :label="t('users.lastAuthenticated')" :value="formatDateTime(user.lastAuthenticatedAt, locale)" />
        <Field :label="t('users.platformRoles')" :value="user.platformRoles?.join(', ') || '—'" />
      </div>

       <div v-if="authStore.hasPermission('platform.users.manage')" class="mb-8 flex flex-wrap gap-3">
         <Button v-if="user.accountState === 'ACTIVE'" variant="outline" @click="disableUser">{{ t('users.disable') }}</Button>
         <Button v-else variant="outline" @click="enableUser">{{ t('users.enable') }}</Button>
         <Button variant="outline" @click="revokeSessions">{{ t('users.revokeSessions') }}</Button>
       </div>
       <div v-if="mutationError" role="alert" class="mb-4 text-destructive">{{ mutationError }}</div>

       <h2 class="mb-3 text-lg font-semibold text-foreground">{{ t('users.workspaces') }}</h2>

       <div v-if="!workspaces.length" class="text-sm text-muted-foreground">{{ t('common.noData') }}</div>
       <Table
         v-else
         :aria-label="t('users.workspaceMemberships')"
       >
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Workspace</TableHead>
            <TableHead scope="col">{{ t('common.status') }}</TableHead>
            <TableHead scope="col">Roles</TableHead>
            <TableHead scope="col">Joined</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="ws in workspaces" :key="ws.workspaceId">
            <TableCell class="text-foreground">{{ ws.workspaceName }}</TableCell>
            <TableCell class="text-muted-foreground">{{ ws.membershipStatus }}</TableCell>
            <TableCell class="text-muted-foreground">{{ ws.workspaceRoles?.join(', ') || '—' }}</TableCell>
            <TableCell class="text-muted-foreground">{{ formatDate(ws.joinedAt, locale) }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>

<script lang="ts">
const Field = defineComponent({
  props: { label: { type: String, required: true }, value: { type: String, required: true } },
  components: { Card, CardContent },
  template: `
    <Card class="p-0">
      <CardContent class="p-4">
        <p class="label-mono mb-1 text-muted-foreground">{{ label }}</p>
        <p class="text-sm text-foreground">{{ value }}</p>
      </CardContent>
    </Card>
  `,
})

export { Field }
</script>
