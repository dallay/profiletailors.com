<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()

const result = ref<PagedResult<AdminUserSummary> | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const search = ref('')
const page = ref(0)

interface AdminUserSummary {
  principalId: string
  email: string | null
  displayIdentity: string | null
  principalType: string
  createdAt: string
  lastAuthenticatedAt: string | null
  platformRoles: string[]
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

async function fetchUsers() {
  loading.value = true
  error.value = null
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '25' })
    if (search.value.trim()) params.set('email', search.value.trim())
    const res = await fetch(`/api/admin/users?${params}`)
    if (!res.ok) throw new Error()
    result.value = await res.json()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

watch(search, () => { page.value = 0; fetchUsers() })
onMounted(fetchUsers)
</script>

<template>
  <div class="p-8">
    <h1 class="text-2xl font-bold text-slate-100 mb-6">{{ t('users.title') }}</h1>

    <div class="mb-4">
      <input
        v-model="search"
        type="search"
        :placeholder="t('waitlist.filters.search')"
        class="bg-slate-800 border border-slate-700 text-slate-200 rounded-lg px-3 py-1.5 text-sm w-64"
        :aria-label="t('waitlist.filters.search')"
      />
    </div>

    <div v-if="loading" class="text-slate-400">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-red-400">{{ error }}</div>
    <template v-else-if="result">
      <table class="w-full text-sm text-left border-collapse" aria-label="Users">
        <thead>
          <tr class="border-b border-slate-800 text-slate-400 uppercase text-xs">
            <th scope="col" class="py-2 pr-4">{{ t('common.email') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('users.displayName') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('users.principalType') }}</th>
            <th scope="col" class="py-2 pr-4">{{ t('common.createdAt') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in result.items"
            :key="user.principalId"
            class="border-b border-slate-800/50 hover:bg-slate-900/50 cursor-pointer"
            @click="router.push({ name: 'user-detail', params: { principalId: user.principalId } })"
          >
            <td class="py-2 pr-4 text-amber-400">{{ user.email }}</td>
            <td class="py-2 pr-4 text-slate-300">{{ user.displayIdentity ?? '—' }}</td>
            <td class="py-2 pr-4 text-slate-300">{{ user.principalType }}</td>
            <td class="py-2 pr-4 text-slate-400">{{ new Date(user.createdAt).toLocaleDateString() }}</td>
          </tr>
        </tbody>
      </table>

      <div class="flex items-center justify-between mt-4 text-sm text-slate-400">
        <span>{{ t('common.page') }} {{ result.page + 1 }} {{ t('common.of') }} {{ result.totalPages }}</span>
        <div class="flex gap-2">
          <button
            :disabled="!result.hasPrevious"
            class="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 hover:bg-slate-700 transition-colors"
            @click="page--; fetchUsers()"
          >{{ t('common.previous') }}</button>
          <button
            :disabled="!result.hasNext"
            class="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 hover:bg-slate-700 transition-colors"
            @click="page++; fetchUsers()"
          >{{ t('common.next') }}</button>
        </div>
      </div>
    </template>
  </div>
</template>
