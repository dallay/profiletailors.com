<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useTakedownReportLoader, useTakedownReportFilters, useRejectReportDialog, useTakedownReportActions } from '@modules/governance/application'

const { t } = useI18n()

// Use composables
const loader = useTakedownReportLoader(t)
const filters = useTakedownReportFilters(loader.reports)
const rejectDialog = useRejectReportDialog()
const actions = useTakedownReportActions(loader.reports, t)
const { isLoading, error: loadError, loadReports } = loader
const { statusFilter, filteredReports } = filters
const { rejectionReason, rejectReportId, openDialog, closeDialog, isReasonEmpty, getReason } = rejectDialog
const { mutatingIds, error: actionError, handleApprove, handleReject } = actions

async function handleApproveClick(reportId: string) {
  await handleApprove(reportId)
}

async function handleRejectClick() {
  if (!rejectReportId.value || isReasonEmpty()) {
    return
  }
  await handleReject(rejectReportId.value, getReason())
  closeDialog()
}

function statusBadgeClass(status: string) {
  switch (status) {
    case 'REPORTED':
      return 'border-warning/30 bg-warning/10 text-warning'
    case 'APPROVED':
      return 'border-success/30 bg-success/10 text-success'
    case 'DISMISSED':
      return 'border-text-secondary/30 bg-text-secondary/10 text-text-secondary'
    default:
      return 'border-border-visible bg-bg-primary text-text-secondary'
  }
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Load on mount
onMounted(() => {
  loadReports()
})
</script>

<template>
  <div class="mx-auto w-full max-w-5xl space-y-6">
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('governance.takedown.review.title') }}
      </h2>
      <p class="text-sm text-text-secondary">
        {{ $t('governance.takedown.review.subtitle') }}
      </p>
    </div>

    <div class="flex items-center gap-3">
      <select
        v-model="statusFilter"
        data-testid="filter-status"
        :aria-label="$t('governance.takedown.review.statusFilter')"
        class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
      >
        <option value="ALL">{{ $t('governance.takedown.review.filterAll') }}</option>
        <option value="REPORTED">{{ $t('governance.takedown.review.statusReported') }}</option>
        <option value="APPROVED">{{ $t('governance.takedown.review.statusApproved') }}</option>
        <option value="DISMISSED">{{ $t('governance.takedown.review.statusDismissed') }}</option>
      </select>

      <Button type="button" variant="outline" size="sm" @click="loadReports">
        {{ $t('governance.takedown.review.refresh') }}
      </Button>
    </div>

    <div v-if="actionError || loadError" class="rounded-xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error">
      {{ actionError || loadError }}
    </div>

    <div v-if="isLoading" class="flex items-center gap-2 text-sm text-text-secondary">
      <span class="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      {{ $t('governance.takedown.review.loading') }}
    </div>

    <div v-else-if="filteredReports.length === 0" class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/30 p-10 text-center">
      <p class="text-sm text-text-display">{{ $t('governance.takedown.review.empty') }}</p>
      <p class="mt-1 text-xs text-text-secondary">{{ $t('governance.takedown.review.emptyHint') }}</p>
    </div>

    <div v-else class="space-y-4">
      <article
        v-for="report in filteredReports"
        :key="report.reportId"
        class="rounded-xl border border-border-subtle bg-bg-primary/40 p-4 space-y-3"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="space-y-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <code class="rounded bg-bg-surface px-1.5 py-0.5 text-xs font-mono">{{ report.assetId }}</code>
              <Badge
                variant="outline"
                class="font-mono text-[10px] uppercase tracking-[0.12em]"
                :class="statusBadgeClass(report.status)"
              >
                {{ report.status }}
              </Badge>
            </div>
            <p class="text-sm text-text-secondary truncate">
              {{ report.reason }}
            </p>
            <div class="flex items-center gap-3 text-xs text-text-secondary">
              <span>{{ report.reporterEmail }}</span>
              <span>·</span>
              <span>{{ formatDate(report.createdAt) }}</span>
            </div>
          </div>

          <div v-if="report.status === 'REPORTED'" class="flex items-center gap-2 shrink-0">
            <Button
              type="button"
              variant="outline"
              size="sm"
              class="text-success border-success/30 hover:bg-success/10"
              :disabled="mutatingIds.has(report.reportId)"
              @click="handleApproveClick(report.reportId)"
            >
              {{ $t('governance.takedown.review.approveAction') }}
            </Button>
            <AlertDialog>
              <AlertDialogTrigger as-child>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  class="text-error border-error/30 hover:bg-error/10"
                  :disabled="mutatingIds.has(report.reportId)"
                  @click="openDialog(report.reportId)"
                >
                  {{ $t('governance.takedown.review.rejectAction') }}
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>{{ $t('governance.takedown.review.rejectDialog.title') }}</AlertDialogTitle>
                  <AlertDialogDescription>
                    {{ $t('governance.takedown.review.rejectDialog.description') }}
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <div class="py-3">
                  <Label for="reject-reason">{{ $t('governance.takedown.review.rejectDialog.reasonLabel') }}</Label>
                  <Textarea
                    id="reject-reason"
                    v-model="rejectionReason"
                    :rows="3"
                    class="mt-1.5"
                    :placeholder="$t('governance.takedown.review.rejectDialog.reasonPlaceholder')"
                  />
                </div>
                <AlertDialogFooter>
                  <AlertDialogCancel @click="closeDialog">{{ $t('workspace.cancel') }}</AlertDialogCancel>
                  <AlertDialogAction
                    class="bg-error text-text-display hover:bg-error/90"
                    :disabled="isReasonEmpty() || mutatingIds.has(report.reportId)"
                    @click="handleRejectClick"
                  >
                    {{ $t('governance.takedown.review.rejectAction') }}
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>

        <div v-if="report.rejectionReason" class="rounded border border-error/20 bg-error/5 p-2 text-xs text-text-secondary">
          <span class="font-medium text-error">{{ $t('governance.takedown.review.rejectionReason') }}:</span>
          {{ report.rejectionReason }}
        </div>
      </article>
    </div>
  </div>
</template>
