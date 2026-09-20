<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PaginationControls, Table } from '@profiletailors/vue-ui'
import { formatDate } from '@/lib/formatters'
import type { PagedResult } from '@/types/pagination'
import { useAdminAuthStore } from '@/stores/auth.store'

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
  <div class="admin-page p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-text-display">{{ t('users.title') }}</h1>

    <div class="mb-4">
      <input
        v-model="search"
        type="search"
        :placeholder="t('waitlist.filters.search')"
        class="admin-input w-64 text-sm"
        :aria-label="t('waitlist.filters.search')"
      />
    </div>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-error">{{ error }}</div>
    <template v-else-if="result">
      <Table aria-label="Users" class="admin-table">
        <thead>
          <tr class="border-b border-border-subtle text-text-secondary uppercase text-xs">
            <th scope="col" class="py-2 pr-4">{{ t('common.email') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('users.displayName') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('users.principalType') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('users.accountState') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('common.createdAt') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in result.items"
            :key="user.principalId"
            class="border-b border-border-subtle hover:bg-bg-surface"
          >
            <td class="py-2 pr-4">
              <RouterLink
                :to="{ name: 'user-detail', params: { principalId: user.principalId } }"
                class="text-text-display hover:underline"
              >
                {{ user.email ?? user.principalId }}
              </RouterLink>
            </td>
            <td class="py-2 pr-4 text-text-body">{{ user.displayIdentity ?? '—' }}</td>
            <td class="py-2 pr-4 text-text-body">{{ user.principalType }}</td>
            <td class="py-2 pr-4" :class="user.accountState === 'DISABLED' ? 'text-error' : 'text-success'">{{ t(`users.accountStates.${user.accountState.toLowerCase()}`) }}</td>
            <td class="py-2 pr-4 text-text-secondary">{{ formatDate(user.createdAt, locale) }}</td>
          </tr>
        </tbody>
      </Table>

      <PaginationControls
        :page="result.page"
        :total-pages="result.totalPages"
        :has-previous="result.hasPrevious"
        :has-next="result.hasNext"
        @previous="page--; fetchUsers()"
        @next="page++; fetchUsers()"
      />
    </template>
  </div>
</template>
