<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

defineOptions({ name: 'AdminCard', inheritAttrs: false })

const props = defineProps<{ class?: HTMLAttributes['class'] }>()
</script>

<template>
  <div v-bind="$attrs" :class="cn('rounded-xl border border-border-subtle bg-bg-surface', props.class)">
    <slot />
  </div>
</template>
