<script setup lang="ts">
import type { InputHTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

defineOptions({ name: 'AdminInput', inheritAttrs: false })

const props = withDefaults(defineProps<{
  class?: InputHTMLAttributes['class']
  modelValue?: string | number
  type?: InputHTMLAttributes['type']
  disabled?: boolean
}>(), {
  modelValue: '',
  type: 'text',
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

function updateValue(event: Event) {
  if (event.target instanceof HTMLInputElement) emit('update:modelValue', event.target.value)
}
</script>

<template>
  <input
    v-bind="$attrs"
    :value="props.modelValue"
    :type="props.type"
    :disabled="props.disabled"
    :class="cn('min-h-11 w-full min-w-0 rounded-lg border border-border-visible bg-bg-primary px-3 py-2 text-sm text-text-body outline-none placeholder:text-text-secondary focus-visible:ring-2 focus-visible:ring-text-display disabled:cursor-not-allowed disabled:opacity-50', props.class)"
    @input="updateValue"
  >
</template>
