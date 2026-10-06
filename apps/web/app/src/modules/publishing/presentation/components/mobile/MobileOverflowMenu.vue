<script setup lang="ts">
import { MoreHorizontal } from '@lucide/vue'
import { Button } from '@profiletailors/vue-ui/shell/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
const emit = defineEmits<{
  (event: 'openBulkImport'): void
  (event: 'startTour'): void
}>()
</script>
<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button data-testid="mobile-overflow-menu" variant="outline" class="min-h-11 min-w-11 size-11 p-0" :aria-label="$t('scheduler.moreActions')">
        <MoreHorizontal class="size-4" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end">
      <DropdownMenuItem data-testid="open-bulk-import" @select="emit('openBulkImport')">
        {{ $t('scheduler.bulkImport') }}
      </DropdownMenuItem>
      <DropdownMenuItem data-testid="start-tour-btn" @select="emit('startTour')">
        {{ $t('scheduler.productTour') }}
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
