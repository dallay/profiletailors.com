<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'
import Button from '@/components/ui/AdminButton.vue'
import Card from '@/components/ui/AdminCard.vue'
import Select from '@/components/ui/AdminSelect.vue'

interface DashboardSummary {
  pendingCount: number
  invitedCount: number
  convertedCount: number
  cancelledCount: number
  activeInvitationCount: number
  invitationsExpiringIn24h: number
  invitationsExpiringIn7d: number
  failedDeliveryCount: number
  registrationsInPeriod: number
  periodDays: number
}

const { t } = useI18n()
const authStore = useAdminAuthStore()
const summary = ref<DashboardSummary | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const periodDays = ref(30)

const operations = computed(() => summary.value ? [
  { label: t('dashboard.pendingEntries'), value: summary.value.pendingCount, tone: 'default' },
  { label: t('dashboard.invitedEntries'), value: summary.value.invitedCount, tone: 'default' },
  { label: t('dashboard.convertedEntries'), value: summary.value.convertedCount, tone: 'success' },
  { label: t('dashboard.cancelledEntries'), value: summary.value.cancelledCount, tone: 'muted' },
] : [])

async function fetchDashboard() {
  loading.value = true
  error.value = null
  try {
    const response = await authStore.request(`/api/admin/dashboard?periodDays=${periodDays.value}`)
    if (!response.ok) throw new Error()
    summary.value = await response.json()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <header class="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-medium text-text-display">{{ t('dashboard.title') }}</h1>
        <p class="mt-2 text-sm text-text-secondary">{{ t('dashboard.subtitle') }}</p>
      </div>
      <label for="dashboard-period" class="flex items-center gap-3 text-sm text-text-secondary">
        <span>{{ t('dashboard.period') }}</span>
        <Select
          id="dashboard-period"
          v-model="periodDays"
          class="w-auto"
          :aria-label="t('dashboard.period')"
          @change="fetchDashboard"
        >
          <option :value="7">{{ t('dashboard.days', { n: 7 }) }}</option>
          <option :value="30">{{ t('dashboard.days', { n: 30 }) }}</option>
          <option :value="90">{{ t('dashboard.days', { n: 90 }) }}</option>
        </Select>
      </label>
    </header>

    <p v-if="loading" role="status" class="py-8 text-sm text-text-secondary">{{ t('common.loading') }}</p>
    <div v-else-if="error" role="alert" class="flex flex-wrap items-center justify-between gap-4 border-y border-error/40 py-5">
      <p class="text-sm text-error">{{ error }}</p>
      <Button variant="secondary" @click="fetchDashboard">{{ t('common.retry') }}</Button>
    </div>

    <template v-else-if="summary">
      <section class="mb-9" :aria-label="t('dashboard.waitlistOverview')">
        <div class="mb-3 flex items-baseline justify-between gap-4">
          <h2 class="text-base font-medium text-text-display">{{ t('dashboard.waitlistOverview') }}</h2>
          <span class="label-mono text-[10px] text-text-secondary">{{ t('dashboard.periodLabel', { n: summary.periodDays }) }}</span>
        </div>
        <div class="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-border-subtle bg-border-subtle sm:grid-cols-4">
          <div v-for="item in operations" :key="item.label" class="bg-bg-primary p-4 sm:p-5">
            <p class="label-mono min-h-8 text-[10px] text-text-secondary">{{ item.label }}</p>
            <p class="mt-2 font-mono text-3xl text-text-display" :class="item.tone === 'success' ? 'text-success' : item.tone === 'muted' ? 'text-text-secondary' : ''">
              {{ item.value }}
            </p>
          </div>
        </div>
      </section>

      <div class="grid gap-9 lg:grid-cols-[minmax(0,1.3fr)_minmax(16rem,0.7fr)]">
        <section :aria-label="t('dashboard.invitationHealth')">
          <h2 class="mb-3 text-base font-medium text-text-display">{{ t('dashboard.invitationHealth') }}</h2>
          <Card class="divide-y divide-border-subtle">
            <div class="flex items-center justify-between gap-4 px-4 py-4 sm:px-5">
              <span class="text-sm text-text-body">{{ t('dashboard.activeInvitations') }}</span>
              <span class="font-mono text-xl text-text-display">{{ summary.activeInvitationCount }}</span>
            </div>
            <div class="flex items-center justify-between gap-4 px-4 py-4 sm:px-5">
              <span class="text-sm text-text-body">{{ t('dashboard.expiringIn24h') }}</span>
              <span class="font-mono text-xl" :class="summary.invitationsExpiringIn24h > 0 ? 'text-warning' : 'text-text-display'">{{ summary.invitationsExpiringIn24h }}</span>
            </div>
            <div class="flex items-center justify-between gap-4 px-4 py-4 sm:px-5">
              <span class="text-sm text-text-body">{{ t('dashboard.expiringIn7d') }}</span>
              <span class="font-mono text-xl text-text-display">{{ summary.invitationsExpiringIn7d }}</span>
            </div>
          </Card>
        </section>

        <section :aria-label="t('dashboard.systemHealth')">
          <h2 class="mb-3 text-base font-medium text-text-display">{{ t('dashboard.systemHealth') }}</h2>
          <Card class="h-full p-4 sm:p-5">
            <p class="label-mono text-[10px] text-text-secondary">{{ t('dashboard.failedDeliveries') }}</p>
            <p class="mt-3 font-mono text-4xl" :class="summary.failedDeliveryCount > 0 ? 'text-warning' : 'text-text-display'">
              {{ summary.failedDeliveryCount }}
            </p>
            <p class="mt-6 border-t border-border-subtle pt-4 text-sm text-text-secondary">
              {{ t('dashboard.registrationsInPeriod') }}
              <span class="ml-2 font-mono text-text-body">{{ summary.registrationsInPeriod }}</span>
            </p>
          </Card>
        </section>
      </div>
    </template>

    <p v-else class="py-8 text-sm text-text-secondary">{{ t('common.noData') }}</p>
  </div>
</template>
