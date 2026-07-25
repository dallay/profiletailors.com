<script setup lang="ts">
import { Trash2 } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'

defineProps<{
  selectedCount: number
}>()

const emit = defineEmits<{
  (e: 'clear-selection'): void
  (e: 'delete-selected'): void
}>()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-primary/30 bg-primary/10 px-5 py-3">
    <div class="flex items-center gap-3">
      <span class="text-sm font-medium text-text-display">
        {{ selectedCount }} {{ $t('media.selectedCountSuffix') }}
      </span>
      <Button type="button" variant="outline" size="sm" @click="emit('clear-selection')">
        {{ $t('media.clearSelection') }}
      </Button>
    </div>
    <div class="flex items-center gap-2">
      <AlertDialog>
        <AlertDialogTrigger as-child>
          <Button type="button" variant="outline" size="sm">
            <Trash2 class="mr-2 size-4" />
            {{ $t('media.deleteSelectedAction') }}
          </Button>
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{{ $t('media.bulkDeleteConfirmTitle') }}</AlertDialogTitle>
            <AlertDialogDescription>
              {{ $t('media.bulkDeleteConfirmBody') }}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{{ $t('workspace.cancel') }}</AlertDialogCancel>
            <AlertDialogAction @click="emit('delete-selected')">
              {{ $t('media.deleteSelectedAction') }}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  </div>
</template>
