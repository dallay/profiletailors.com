<script setup lang="ts">
import { Button } from '@/components/ui/button'

const emit = defineEmits<{
  (e: 'reconnect'): void
}>()
</script>

<template>
  <div class="flex shrink-0 items-center gap-3 rounded-xl border border-warning/30 bg-warning/5 px-4 py-3">
    <span class="font-mono text-[10px] font-bold tracking-wider uppercase text-warning">
      Reconnect Required
    </span>
    <span class="text-xs text-text-secondary">
      Some LinkedIn accounts need re-authentication to resume publishing.
    </span>
    <Button
      class="ml-auto gap-1.5 text-[10px] uppercase font-mono tracking-wider bg-warning/10 text-warning border border-warning/30 hover:bg-warning/20"
      @click="emit('reconnect')"
    >
      Reconnect
    </Button>
  </div>
</template>
