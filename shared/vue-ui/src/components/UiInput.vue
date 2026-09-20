<script setup lang="ts">
import type { InputHTMLAttributes } from 'vue'
import { cn } from '../lib/utils'

defineOptions({ inheritAttrs: false })

interface Props {
  class?: InputHTMLAttributes['class']
  modelValue?: string | number
  type?: InputHTMLAttributes['type']
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  type: 'text',
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <input
    v-bind="$attrs"
    :value="props.modelValue"
    :type="props.type"
    :disabled="props.disabled"
    :class="cn(
      'min-h-11 w-full min-w-0 rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-text-body outline-none placeholder:text-text-secondary focus-visible:ring-2 focus-visible:ring-text-display disabled:cursor-not-allowed disabled:opacity-50',
      props.class,
    )"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  >
</template>
