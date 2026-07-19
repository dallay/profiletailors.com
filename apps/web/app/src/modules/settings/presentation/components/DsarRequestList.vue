<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DsarRequest } from '@modules/settings/infrastructure/privacy.store'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableEmpty,
} from '@/components/ui/table'
import DsarStatusBadge from './DsarStatusBadge.vue'

const props = defineProps<{
  requests: DsarRequest[]
  loading: boolean
}>()

const { t } = useI18n()

const typeLabelKey = (type: DsarRequest['type']): string => {
  return `settings.privacy.form.type.${type}`
}

const formattedDate = (iso: string): string => {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return iso
  }
}

const sortedRequests = computed(() => {
  return [...props.requests].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )
})
</script>

<template>
  <div>
    <div class="rounded-2xl border border-border-subtle overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{{ t('settings.privacy.list.columns.type') }}</TableHead>
            <TableHead>{{ t('settings.privacy.list.columns.status') }}</TableHead>
            <TableHead>{{ t('settings.privacy.list.columns.created') }}</TableHead>
            <TableHead class="text-right">{{ t('settings.privacy.list.columns.actions') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableEmpty v-if="!loading && sortedRequests.length === 0" :colspan="4">
            <p class="text-sm text-text-secondary">
              {{ t('settings.privacy.list.empty') }}
            </p>
          </TableEmpty>

          <TableRow
            v-for="request in sortedRequests"
            :key="request.id"
            data-testid="dsar-request-row"
          >
            <TableCell class="font-medium">
              {{ t(typeLabelKey(request.type)) }}
            </TableCell>
            <TableCell>
              <DsarStatusBadge :status="request.status" />
            </TableCell>
            <TableCell class="text-text-secondary text-sm">
              {{ formattedDate(request.createdAt) }}
            </TableCell>
            <TableCell class="text-right">
              <Button
                v-if="request.type === 'EXPORT' && request.status === 'COMPLETED' && request.resultRef"
                variant="outline"
                size="sm"
                as-child
                data-testid="dsar-download-btn"
              >
                <a
                  :href="`/api/v1/privacy/requests/${request.id}/download`"
                  :download="request.resultRef"
                >
                  {{ t('settings.privacy.list.download') }}
                </a>
              </Button>
            </TableCell>
          </TableRow>

          <TableRow v-if="loading">
            <TableCell :colspan="4">
              <p class="text-sm text-text-secondary text-center py-4 font-mono text-[10px] uppercase tracking-[0.14em]">
                {{ t('common.loading') || 'Loading...' }}
              </p>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
