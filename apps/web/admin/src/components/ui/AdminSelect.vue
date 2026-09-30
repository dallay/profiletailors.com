<script setup lang="ts">
import type { SelectHTMLAttributes } from 'vue'
import { useId } from 'vue'
import { cn } from '@/lib/utils'

defineOptions({ name: 'AdminSelect', inheritAttrs: false })

const props = withDefaults(defineProps<{
  class?: SelectHTMLAttributes['class']
  id?: string
  modelValue?: string | number
  disabled?: boolean
}>(), {
  modelValue: '',
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const selectId = props.id ?? useId()

function updateValue(event: Event) {
  if (event.target instanceof HTMLSelectElement) emit('update:modelValue', event.target.value)
}
</script>

<template>
  <select
    v-bind="$attrs"
    :id="selectId"
    :value="props.modelValue"
    :disabled="props.disabled"
    :class="cn('min-h-11 min-w-0 rounded-lg border border-border-visible bg-bg-primary px-3 py-2 text-sm text-text-body outline-none focus-visible:ring-2 focus-visible:ring-text-display disabled:cursor-not-allowed disabled:opacity-50', props.class)"
    @change="updateValue"
  >
    <slot />
  </select>
</template>
