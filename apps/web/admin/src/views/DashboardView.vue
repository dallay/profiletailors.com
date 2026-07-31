<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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

const summary = ref<DashboardSummary | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const periodDays = ref(30)

async function fetchDashboard() {
  loading.value = true
  error.value = null
  try {
    const res = await fetch(`/api/admin/dashboard?periodDays=${periodDays.value}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    summary.value = await res.json()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div class="p-8">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-slate-100">{{ t('dashboard.title') }}</h1>
      <select
        v-model="periodDays"
        class="bg-slate-800 text-slate-300 border border-slate-700 rounded-lg px-3 py-1.5 text-sm"
        :aria-label="t('dashboard.period')"
        @change="fetchDashboard"
      >
        <option :value="7">{{ t('dashboard.days', { n: 7 }) }}</option>
        <option :value="30">{{ t('dashboard.days', { n: 30 }) }}</option>
        <option :value="90">{{ t('dashboard.days', { n: 90 }) }}</option>
      </select>
    </div>

    <div v-if="loading" class="text-slate-400">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="text-red-400">{{ error }}</div>
    <div v-else-if="summary" class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <StatCard :label="t('dashboard.pendingEntries')" :value="summary.pendingCount" />
      <StatCard :label="t('dashboard.invitedEntries')" :value="summary.invitedCount" />
      <StatCard :label="t('dashboard.convertedEntries')" :value="summary.convertedCount" />
      <StatCard :label="t('dashboard.cancelledEntries')" :value="summary.cancelledCount" />
      <StatCard :label="t('dashboard.activeInvitations')" :value="summary.activeInvitationCount" />
      <StatCard :label="t('dashboard.expiringIn24h')" :value="summary.invitationsExpiringIn24h" warn />
      <StatCard :label="t('dashboard.expiringIn7d')" :value="summary.invitationsExpiringIn7d" />
      <StatCard :label="t('dashboard.failedDeliveries')" :value="summary.failedDeliveryCount" warn />
      <StatCard :label="t('dashboard.registrationsInPeriod')" :value="summary.registrationsInPeriod" />
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

const StatCard = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: Number, required: true },
    warn: { type: Boolean, default: false },
  },
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-xl p-5">
      <p class="text-xs text-slate-400 uppercase tracking-wide mb-1">{{ label }}</p>
      <p class="text-3xl font-bold" :class="warn && value > 0 ? 'text-amber-400' : 'text-slate-100'">{{ value }}</p>
    </div>
  `,
})

export { StatCard }
</script>
