<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '../lib/utils'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div
    v-bind="$attrs"
    :class="cn('flex min-h-24 w-full items-center justify-center rounded-xl border border-dashed border-border-subtle p-6 text-center text-text-secondary', props.class)"
  >
    <slot />
  </div>
</template>
