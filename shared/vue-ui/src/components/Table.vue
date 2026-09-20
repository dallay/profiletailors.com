<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '../lib/utils'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div class="relative w-full overflow-x-auto">
    <table
      v-bind="$attrs"
      :class="cn('w-full text-left text-sm', props.class)"
    >
      <slot />
    </table>
  </div>
</template>
