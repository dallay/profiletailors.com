<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useBulkImport } from '@modules/publishing/application/useBulkImport'
import type { BulkTemplate } from '@modules/publishing/domain/bulk'

const emit = defineEmits<{
  (e: 'select', template: BulkTemplate): void
  (e: 'download', csv: string): void
}>()

const { loadTemplates, downloadTemplateCsv } = useBulkImport()
const templates = ref<BulkTemplate[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)
const downloadError = ref<string | null>(null)

onMounted(async () => {
  loading.value = true
  loadError.value = null
  try {
    const result = await loadTemplates()
    templates.value = result.templates
  } catch (e) {
    templates.value = []
    loadError.value = e instanceof Error ? e.message : 'Failed to load templates'
  } finally {
    loading.value = false
  }
})

async function handleSelect(t: BulkTemplate) {
  emit('select', t)
  downloadError.value = null
  try {
    const csv = await downloadTemplateCsv(t.id)
    emit('download', csv)
  } catch (e) {
    downloadError.value = e instanceof Error ? e.message : 'Failed to download template'
  }
}
</script>

<template>
  <div data-testid="bulk-template-picker">
    <p v-if="loading" data-testid="bulk-templates-loading">Loading templates…</p>
    <p v-if="loadError" data-testid="bulk-templates-error" class="text-sm text-error">{{ loadError }}</p>
    <p v-if="downloadError" data-testid="bulk-template-download-error" class="text-sm text-error">{{ downloadError }}</p>
    <ul v-else-if="!loading" data-testid="bulk-templates-list" class="space-y-2">
      <li v-for="t in templates" :key="t.id">
        <button :data-testid="`bulk-template-${t.id}`" class="w-full rounded border px-3 py-2 text-left hover:bg-bg-primary" @click="handleSelect(t)">
          <span class="font-medium">{{ t.name }}</span>
          <span class="ml-2 text-xs text-text-secondary">{{ t.description }}</span>
        </button>
      </li>
      <li v-if="templates.length === 0" data-testid="bulk-templates-empty" class="text-sm text-text-secondary">No templates</li>
    </ul>
  </div>
</template>
