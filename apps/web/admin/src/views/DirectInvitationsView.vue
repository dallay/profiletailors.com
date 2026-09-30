<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'
import { formatDateTime } from '@/lib/formatters'
import type { PagedResult } from '@/types/pagination'
import RevokeInvitationDialog from '@/components/RevokeInvitationDialog.vue'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldLabel } from '@/components/ui/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ChevronLeftIcon, ChevronRightIcon, SearchIcon } from '@lucide/vue'

interface CreatedInvitation {
  id: string
  email: string
  status: string
  expiresAt: string
  version: number
}

interface DirectInvitationRow {
  invitationId: string
  email: string
  target: string
  workspaceId: string | null
  status: string
  expiresAt: string
  version: number
}

type DirectInvitationPage = PagedResult<DirectInvitationRow>

const JSON_API_MEDIA_TYPE = 'application/vnd.api.v1+json'

type DirectInvitationTarget = 'NEW_WORKSPACE' | 'EXISTING_WORKSPACE'

const { t, locale } = useI18n()
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

const listResult = ref<DirectInvitationPage | null>(null)
const listLoading = ref(false)
const listError = ref<string | null>(null)
const listSearch = ref('')
const listStatusFilter = ref('')
const listPage = ref(0)
const rowActionId = ref<string | null>(null)

const listItems = computed(() => listResult.value?.items ?? [])

let activeListRequest: AbortController | null = null

function invitationStatusLabel(status: string) {
  const key = `directInvitations.list.statuses.${status.toLowerCase()}`
  const translated = t(key)
  return translated === key ? status : translated
}

function invitationTargetLabel(target: string) {
  const keyByValue: Record<string, string> = {
    EXISTING_WORKSPACE: 'existingWorkspace',
    NEW_WORKSPACE: 'newWorkspace',
  }
  const mapped = keyByValue[target]
  if (!mapped) return target
  const key = `directInvitations.list.targets.${mapped}`
  const translated = t(key)
  return translated === key ? target : translated
}

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

function openRevokeDialog(id: string, email?: string, expectedVersion?: number) {
  const invitation = created.value && created.value.id === id ? created.value : null
  revokeTarget.value = {
    id,
    email: email ?? invitation?.email ?? id,
    expectedVersion: expectedVersion ?? invitation?.version ?? 0,
  }
  revokeError.value = null
  revokeDialogOpen.value = true
}

async function fetchList() {
  if (!canRead) return
  activeListRequest?.abort()
  const controller = new AbortController()
  activeListRequest = controller
  listLoading.value = true
  listError.value = null
  try {
    const params = new URLSearchParams({ page: String(listPage.value), size: '25' })
    if (listStatusFilter.value) params.set('status', listStatusFilter.value)
    if (listSearch.value.trim()) params.set('email', listSearch.value.trim())
    const res = await authStore.request(`/api/admin/invitations/direct?${params}`, {
      signal: controller.signal,
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    listResult.value = (await res.json()) as DirectInvitationPage
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    listResult.value = null
    listError.value = t('common.error')
  } finally {
    if (activeListRequest === controller) {
      activeListRequest = null
      listLoading.value = false
    }
  }
}

async function resendRow(row: DirectInvitationRow) {
  if (rowActionId.value) return
  rowActionId.value = row.invitationId
  try {
    const res = await authStore.request(
      `/api/admin/invitations/${row.invitationId}/direct-resend`,
      {
        method: 'POST',
        headers: { 'Content-Type': JSON_API_MEDIA_TYPE },
      },
    )
    if (!res.ok) {
      listError.value = await errorMessage(res)
      return
    }
    await fetchList()
  } catch {
    listError.value = t('common.error')
  } finally {
    rowActionId.value = null
  }
}

function openRowRevokeDialog(row: DirectInvitationRow) {
  openRevokeDialog(row.invitationId, row.email, row.version)
}

watch([listStatusFilter, listSearch], () => {
  listPage.value = 0
  void fetchList()
})

onMounted(() => {
  if (canRead) void fetchList()
})
onBeforeUnmount(() => {
  activeListRequest?.abort()
})

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
    if (canRead) await fetchList()
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
</script>

<template>
  <div class="p-5 sm:p-8">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold text-foreground">
        {{ t('directInvitations.title') }}
      </h1>
      <p class="mt-1 text-sm text-muted-foreground">
        {{ t('directInvitations.subtitle') }}
      </p>
    </header>

    <Card
      v-if="canCreate"
      class="mb-6 p-5"
      data-testid="direct-invitation-form-card"
    >
      <CardHeader class="p-0 mb-4">
        <CardTitle class="text-lg font-medium text-foreground">
          {{ t('directInvitations.form.title') }}
        </CardTitle>
      </CardHeader>

      <CardContent class="p-0">
        <form class="space-y-4" novalidate @submit.prevent="submit">
          <Field class="space-y-1">
            <FieldLabel
              for="direct-invitation-email"
              class="label-mono mb-1 text-muted-foreground"
            >
              {{ t('common.email') }}
            </FieldLabel>
            <Input
              id="direct-invitation-email"
              v-model="form.email"
              type="email"
              autocomplete="off"
              required
              class="w-full text-sm"
              data-testid="direct-invitation-email"
              :placeholder="t('directInvitations.form.emailPlaceholder')"
              :disabled="submitting"
            />
          </Field>

          <Field class="space-y-1">
            <FieldLabel
              for="direct-invitation-target"
              class="label-mono mb-1 text-muted-foreground"
            >
              {{ t('directInvitations.form.target') }}
            </FieldLabel>
            <select
              id="direct-invitation-target"
              v-model="form.target"
              class="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
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
          </Field>

          <Field v-if="workspaceRequired" class="space-y-1">
            <FieldLabel
              for="direct-invitation-workspace"
              class="label-mono mb-1 text-muted-foreground"
            >
              {{ t('directInvitations.form.workspaceId') }}
            </FieldLabel>
            <Input
              id="direct-invitation-workspace"
              v-model="form.workspaceId"
              type="text"
              autocomplete="off"
              required
              class="w-full text-sm"
              data-testid="direct-invitation-workspace"
              :placeholder="t('directInvitations.form.workspaceIdPlaceholder')"
              :disabled="submitting"
            />
          </Field>

          <Alert
            v-if="formError"
            variant="destructive"
            data-testid="direct-invitation-error"
          >
            <AlertDescription>{{ formError }}</AlertDescription>
          </Alert>

          <div class="flex items-center gap-3">
            <Button
              type="submit"
              size="sm"
              data-testid="direct-invitation-submit"
              :disabled="!formValid || submitting"
            >
              {{ submitting ? t('common.loading') : t('directInvitations.form.submit') }}
            </Button>
            <span
              v-if="!emailValid && form.email.length > 0"
              class="text-xs text-warning"
            >
              {{ t('directInvitations.form.emailInvalid') }}
            </span>
          </div>
        </form>
      </CardContent>
    </Card>

    <Card
      v-if="created"
      class="p-5"
      data-testid="direct-invitation-success"
    >
      <CardHeader class="p-0 mb-3">
        <CardTitle class="text-lg font-medium text-success">
          {{ t('directInvitations.success.title') }}
        </CardTitle>
      </CardHeader>
      <CardContent class="p-0">
        <dl class="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt class="label-mono mb-1 text-muted-foreground">
              {{ t('directInvitations.success.id') }}
            </dt>
            <dd class="font-mono text-foreground">
              {{ created.id }}
            </dd>
          </div>
          <div>
            <dt class="label-mono mb-1 text-muted-foreground">
              {{ t('common.status') }}
            </dt>
            <dd class="text-foreground">
              {{ created.status }}
            </dd>
          </div>
          <div>
            <dt class="label-mono mb-1 text-muted-foreground">
              {{ t('directInvitations.success.expiresAt') }}
            </dt>
            <dd class="text-foreground">
              {{ formatDateTime(created.expiresAt, locale) }}
            </dd>
          </div>
        </dl>
        <div class="mt-4 flex flex-wrap gap-3">
          <Button
            v-if="canResend"
            type="button"
            variant="outline"
            size="sm"
            data-testid="direct-invitation-resend"
            :disabled="resending"
            @click="resendInvitation"
          >
            {{ resending ? t('common.loading') : t('directInvitations.success.resend') }}
          </Button>
          <Button
            v-if="canRevoke"
            type="button"
            variant="destructive"
            size="sm"
            data-testid="direct-invitation-revoke"
            @click="openRevokeDialog(created.id)"
          >
            {{ t('directInvitations.success.revoke') }}
          </Button>
          <span
            v-if="!canRevoke && !canResend && !canRead"
            class="text-xs text-muted-foreground"
          >
            {{ t('directInvitations.success.readOnlyNotice') }}
          </span>
        </div>
        <Alert
          v-if="resendError"
          variant="destructive"
          class="mt-3"
          data-testid="direct-invitation-resend-error"
        >
          <AlertDescription>{{ resendError }}</AlertDescription>
        </Alert>
      </CardContent>
    </Card>

    <p v-if="!canRead" class="text-sm text-muted-foreground">
      {{ t('auth.accessDeniedMessage') }}
    </p>

    <Card
      v-if="canRead"
      class="mt-6 p-5"
      data-testid="direct-invitations-list"
    >
      <CardHeader class="p-0 mb-4">
        <CardTitle class="text-lg font-medium text-foreground">
          {{ t('directInvitations.list.title') }}
        </CardTitle>
      </CardHeader>

      <CardContent class="p-0">
        <div class="mb-4 flex flex-wrap gap-3">
          <div class="relative w-64">
            <SearchIcon class="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
            <Input
              v-model="listSearch"
              type="search"
              class="pl-8 text-sm"
              data-testid="direct-invitations-search"
              :placeholder="t('directInvitations.list.search')"
              :aria-label="t('directInvitations.list.search')"
            />
          </div>
          <select
            v-model="listStatusFilter"
            class="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
            data-testid="direct-invitations-status-filter"
            :aria-label="t('directInvitations.list.statusFilter')"
          >
            <option value="">
              {{ t('directInvitations.list.allStatuses') }}
            </option>
            <option value="ACTIVE">
              {{ t('directInvitations.list.statuses.active') }}
            </option>
            <option value="ACCEPTED">
              {{ t('directInvitations.list.statuses.accepted') }}
            </option>
            <option value="EXPIRED">
              {{ t('directInvitations.list.statuses.expired') }}
            </option>
            <option value="REVOKED">
              {{ t('directInvitations.list.statuses.revoked') }}
            </option>
          </select>
        </div>

        <div
          v-if="listLoading"
          class="text-sm text-muted-foreground"
          data-testid="direct-invitations-loading"
        >
          {{ t('common.loading') }}
        </div>
        <Alert
          v-else-if="listError"
          variant="destructive"
          data-testid="direct-invitations-error"
        >
          <AlertDescription>{{ listError }}</AlertDescription>
        </Alert>
        <template v-else-if="listResult">
          <div
            v-if="listItems.length === 0"
            class="text-sm text-muted-foreground"
            data-testid="direct-invitations-empty"
          >
            {{ t('directInvitations.list.empty') }}
          </div>
          <template v-else>
            <Table
              data-testid="direct-invitations-table"
              :aria-label="t('directInvitations.list.title')"
            >
              <TableHeader>
                <TableRow>
                  <TableHead scope="col">
                    {{ t('common.email') }}
                  </TableHead>
                  <TableHead scope="col">
                    {{ t('directInvitations.list.target') }}
                  </TableHead>
                  <TableHead scope="col">
                    {{ t('common.status') }}
                  </TableHead>
                  <TableHead scope="col">
                    {{ t('directInvitations.list.expiresAt') }}
                  </TableHead>
                  <TableHead scope="col">
                    {{ t('common.actions') }}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow
                  v-for="row in listItems"
                  :key="row.invitationId"
                  data-testid="direct-invitation-row"
                >
                  <TableCell class="font-medium text-foreground">
                    {{ row.email }}
                  </TableCell>
                  <TableCell class="text-muted-foreground">
                    {{ invitationTargetLabel(row.target) }}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" class="font-mono text-[10px] uppercase">
                      {{ invitationStatusLabel(row.status) }}
                    </Badge>
                  </TableCell>
                  <TableCell class="text-muted-foreground">
                    {{ formatDateTime(row.expiresAt, locale) }}
                  </TableCell>
                  <TableCell class="flex gap-2">
                    <Button
                      v-if="canResend && row.status === 'ACTIVE'"
                      type="button"
                      variant="outline"
                      size="xs"
                      data-testid="direct-invitation-row-resend"
                      :disabled="rowActionId === row.invitationId"
                      @click="resendRow(row)"
                    >
                      {{ t('directInvitations.success.resend') }}
                    </Button>
                    <Button
                      v-if="canRevoke && row.status === 'ACTIVE'"
                      type="button"
                      variant="destructive"
                      size="xs"
                      data-testid="direct-invitation-row-revoke"
                      @click="openRowRevokeDialog(row)"
                    >
                      {{ t('directInvitations.success.revoke') }}
                    </Button>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            <nav class="mt-4 flex items-center justify-between gap-3 text-sm text-muted-foreground" aria-label="Pagination">
              <span data-testid="direct-invitations-page-info">
                {{ t('common.page') }} {{ listResult.page + 1 }} {{ t('common.of') }} {{ listResult.totalPages }}
              </span>
              <div class="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  data-testid="direct-invitations-prev"
                  :disabled="!listResult.hasPrevious"
                  :aria-label="t('common.previous')"
                  @click="listPage--; fetchList()"
                >
                  <ChevronLeftIcon class="mr-1 size-3.5" />
                  {{ t('common.previous') }}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  data-testid="direct-invitations-next"
                  :disabled="!listResult.hasNext"
                  :aria-label="t('common.next')"
                  @click="listPage++; fetchList()"
                >
                  {{ t('common.next') }}
                  <ChevronRightIcon class="ml-1 size-3.5" />
                </Button>
              </div>
            </nav>
          </template>
        </template>
      </CardContent>
    </Card>

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
