<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { formatDate } from '@/lib/formatters'
import type { PagedResult } from '@/types/pagination'
import { useAdminAuthStore } from '@/stores/auth.store'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
const authStore = useAdminAuthStore()

const result = ref<PagedResult<AdminUserSummary> | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const search = ref('')
const page = ref(0)

let searchTimer: ReturnType<typeof setTimeout> | null = null
let activeRequest: AbortController | null = null

interface AdminUserSummary {
  principalId: string
  email: string | null
  displayIdentity: string | null
  principalType: string
  accountState: 'ACTIVE' | 'DISABLED'
  emailStatus: string | null
  createdAt: string
  lastAuthenticatedAt: string | null
  platformRoles: string[]
}

async function fetchUsers() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '25' })
    if (search.value.trim()) params.set('email', search.value.trim())
    const res = await authStore.request(`/api/admin/users?${params}`, { signal: controller.signal })
    if (!res.ok) throw new Error()
    result.value = await res.json()
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

watch(search, () => {
  page.value = 0
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(fetchUsers, 300)
})
onMounted(fetchUsers)
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  activeRequest?.abort()
})
</script>

<template>
  <div class="p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-foreground">{{ t('users.title') }}</h1>

    <div class="relative mb-4 w-64">
      <SearchIcon class="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
      <Input
        v-model="search"
        type="search"
        :placeholder="t('waitlist.filters.search')"
        class="pl-8 text-sm"
        :aria-label="t('waitlist.filters.search')"
      />
    </div>

    <div v-if="loading" class="text-muted-foreground">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-destructive">{{ error }}</div>
    <template v-else-if="result">
      <Table aria-label="Users">
        <TableHeader>
          <TableRow>
            <TableHead scope="col">{{ t('common.email') }}</TableHead>
            <TableHead scope="col">{{ t('users.displayName') }}</TableHead>
            <TableHead scope="col">{{ t('users.principalType') }}</TableHead>
            <TableHead scope="col">{{ t('users.accountState') }}</TableHead>
            <TableHead scope="col">{{ t('common.createdAt') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="user in result.items"
            :key="user.principalId"
          >
            <TableCell>
              <RouterLink
                :to="{ name: 'user-detail', params: { principalId: user.principalId } }"
                class="font-medium text-foreground hover:underline"
              >
                {{ user.email ?? user.principalId }}
              </RouterLink>
            </TableCell>
            <TableCell class="text-foreground">{{ user.displayIdentity ?? '—' }}</TableCell>
            <TableCell class="text-foreground">{{ user.principalType }}</TableCell>
            <TableCell>
              <Badge
                :variant="user.accountState === 'DISABLED' ? 'destructive' : 'outline'"
                class="text-[10px] uppercase font-mono"
              >
                {{ t(`users.accountStates.${user.accountState.toLowerCase()}`) }}
              </Badge>
            </TableCell>
            <TableCell class="text-muted-foreground">{{ formatDate(user.createdAt, locale) }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <nav class="mt-4 flex items-center justify-between gap-3" aria-label="Pagination">
        <Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="!result.hasPrevious"
          @click="page--; fetchUsers()"
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
          @click="page++; fetchUsers()"
        >
          {{ t('common.next') }}
          <ChevronRightIcon class="ml-1 size-3.5" />
        </Button>
      </nav>
    </template>
  </div>
</template>
