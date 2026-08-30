<script setup lang="ts">
import { ref, computed } from 'vue'
import { useBulkCsvParser } from '@modules/publishing/application/useBulkCsvParser'
import { useBulkImport } from '@modules/publishing/application/useBulkImport'
import { BULK_CANONICAL_HEADER } from '@modules/publishing/domain/bulk'
import BulkPreviewTable from './BulkPreviewTable.vue'
import BulkTemplatePicker from './BulkTemplatePicker.vue'

const props = withDefaults(defineProps<{ isOpen?: boolean }>(), { isOpen: false })
const emit = defineEmits<{ (e: 'close'): void; (e: 'scheduled', jobId: string): void }>()

const csvText = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const { parse } = useBulkCsvParser()
const bulk = useBulkImport()

const parsed = computed(() => parse(csvText.value))

async function handleFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  csvText.value = await file.text()
}

function handleTemplateCsv(csv: string) {
  csvText.value = csv
}

async function handleValidate() {
  if (!csvText.value.trim()) return
  await bulk.validate(csvText.value)
}

async function handleSchedule() {
  if (!csvText.value.trim()) return
  const result = await bulk.schedule(csvText.value)
  emit('scheduled', result.jobId)
}

function handleClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="props.isOpen" data-testid="bulk-import-modal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" @click.self="handleClose">
      <div class="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl border bg-bg-surface p-6">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-bold">Bulk Import</h2>
          <button data-testid="bulk-modal-close" class="rounded border px-2 py-1" @click="handleClose">Close</button>
        </div>

        <div class="mt-4 space-y-4 overflow-auto">
          <BulkTemplatePicker @download="handleTemplateCsv" />

          <div>
            <label class="text-sm font-medium">CSV file</label>
            <input ref="fileInput" data-testid="bulk-file-input" type="file" accept=".csv,text/csv" class="mt-1 block w-full text-sm" @change="handleFile">
            <p class="mt-1 text-xs text-text-secondary">Header: {{ BULK_CANONICAL_HEADER }}</p>
          </div>

          <div>
            <label class="text-sm font-medium">CSV text</label>
            <textarea v-model="csvText" data-testid="bulk-csv-textarea" rows="6" class="mt-1 w-full rounded border p-2 font-mono text-xs" :placeholder="BULK_CANONICAL_HEADER + '\nHello world,2026-06-15T10:00:00Z,UTC,,'"></textarea>
            <p v-if="parsed.headerValid === false && csvText.trim()" data-testid="bulk-header-error" class="mt-1 text-xs text-error">Invalid header — expected {{ BULK_CANONICAL_HEADER }}</p>
          </div>

          <div class="flex gap-2">
            <button data-testid="bulk-validate-btn" class="rounded bg-primary px-4 py-2 text-sm text-white disabled:opacity-50" :disabled="bulk.isValidating.value || !csvText.trim()" @click="handleValidate">
              {{ bulk.isValidating.value ? 'Validating…' : 'Validate' }}
            </button>
            <button data-testid="bulk-schedule-btn" class="rounded border px-4 py-2 text-sm disabled:opacity-50" :disabled="bulk.isScheduling.value || !bulk.validateResult.value" @click="handleSchedule">
              {{ bulk.isScheduling.value ? 'Scheduling…' : 'Schedule' }}
            </button>
          </div>

          <p v-if="bulk.error.value" data-testid="bulk-error" class="text-sm text-error">{{ bulk.error.value }}</p>

          <BulkPreviewTable v-if="bulk.validateResult.value" :rows="bulk.validateResult.value.rows" :editable="true" />

          <div v-if="bulk.scheduleResult.value" data-testid="bulk-schedule-result" class="rounded border p-3 text-sm">
            <p>Job {{ bulk.scheduleResult.value.jobId }} — scheduled {{ bulk.scheduleResult.value.scheduledCount }}/{{ bulk.scheduleResult.value.totalRows }}</p>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
