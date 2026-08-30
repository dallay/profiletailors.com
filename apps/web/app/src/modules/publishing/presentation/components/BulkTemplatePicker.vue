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

onMounted(async () => {
  loading.value = true
  try {
    const result = await loadTemplates()
    templates.value = result.templates
  } catch {
    templates.value = []
  } finally {
    loading.value = false
  }
})

async function handleSelect(t: BulkTemplate) {
  emit('select', t)
  try {
    const csv = await downloadTemplateCsv(t.id)
    emit('download', csv)
  } catch {}
}
</script>

<template>
  <div data-testid="bulk-template-picker">
    <p v-if="loading" data-testid="bulk-templates-loading">Loading templates…</p>
    <ul v-else data-testid="bulk-templates-list" class="space-y-2">
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
