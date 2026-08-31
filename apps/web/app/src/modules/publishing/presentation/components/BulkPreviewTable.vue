<script setup lang="ts">
import type { BulkRowValidation } from '@modules/publishing/domain/bulk'

defineProps<{
  rows: BulkRowValidation[]
  editable?: boolean
}>()

defineEmits<{
  (e: 'update:bodyText', rowIndex: number, value: string): void
  (e: 'update:scheduledFor', rowIndex: number, value: string): void
}>()
</script>

<template>
  <div data-testid="bulk-preview-table" class="overflow-auto rounded-xl border border-border-subtle">
    <table class="w-full text-sm">
      <thead class="bg-bg-primary">
        <tr>
          <th class="px-3 py-2 text-left">#</th>
          <th class="px-3 py-2 text-left">Status</th>
          <th class="px-3 py-2 text-left">bodyText</th>
          <th class="px-3 py-2 text-left">scheduledFor</th>
          <th class="px-3 py-2 text-left">Errors</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.rowIndex" :data-testid="`bulk-row-${row.rowIndex}`" :data-status="row.status">
          <td class="px-3 py-2">{{ row.rowIndex + 1 }}</td>
          <td class="px-3 py-2">
            <span :data-testid="`bulk-row-status-${row.rowIndex}`" :class="row.status === 'VALID' ? 'text-success' : 'text-error'">{{ row.status }}</span>
          </td>
          <td class="px-3 py-2">
            <input
              v-if="editable"
              :data-testid="`bulk-row-body-${row.rowIndex}`"
              :value="row.bodyText ?? ''"
              class="w-full rounded border px-2 py-1"
              @input="$emit('update:bodyText', row.rowIndex, ($event.target as HTMLInputElement).value)"
            >
            <span v-else>{{ row.bodyText }}</span>
          </td>
          <td class="px-3 py-2">
            <input
              v-if="editable"
              :data-testid="`bulk-row-scheduled-${row.rowIndex}`"
              :value="row.scheduledFor ?? ''"
              class="w-full rounded border px-2 py-1"
              @input="$emit('update:scheduledFor', row.rowIndex, ($event.target as HTMLInputElement).value)"
            >
            <span v-else>{{ row.scheduledFor }}</span>
          </td>
          <td class="px-3 py-2">
            <ul v-if="row.errors.length" :data-testid="`bulk-row-errors-${row.rowIndex}`" class="space-y-1">
              <li v-for="err in row.errors" :key="err.code" :data-testid="`bulk-error-${row.rowIndex}-${err.code}`" class="text-xs" :class="err.code === 'DUPLICATE' ? 'text-warning' : 'text-error'">
                {{ err.code }}: {{ err.message }}
              </li>
            </ul>
            <span v-else data-testid="bulk-row-no-error" class="text-xs text-success">—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="rows.length === 0" data-testid="bulk-preview-empty" class="p-4 text-center text-sm text-text-secondary">No rows</p>
  </div>
</template>
