<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

withDefaults(defineProps<{
  id: string
  label: string
  modelValue: string
  autocomplete?: string
  error?: string
  readonly?: boolean
}>(), { autocomplete: 'current-password', error: undefined, readonly: false })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const visible = ref(false)
const input = ref<HTMLInputElement | null>(null)
const { t } = useI18n()
defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <div class="space-y-2">
    <label :for="id" class="text-sm font-medium text-text-display">{{ label }}</label>
    <div class="relative">
      <input
        ref="input"
        :id="id"
        :value="modelValue"
        :type="visible ? 'text' : 'password'"
        :autocomplete="autocomplete"
        :readonly="readonly"
        :aria-invalid="error ? 'true' : 'false'"
        :aria-describedby="error ? `${id}-error` : undefined"
        class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4 pr-24 text-text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      >
      <button
        type="button"
        :aria-label="t(visible ? 'auth.hidePassword' : 'auth.showPassword')"
        :aria-pressed="visible"
        :disabled="readonly"
        class="absolute right-2 top-1/2 min-h-9 -translate-y-1/2 rounded-xl px-3 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display disabled:opacity-60"
        @click="visible = !visible"
      >
        {{ t(visible ? 'auth.hide' : 'auth.show') }}
      </button>
    </div>
    <p v-if="error" :id="`${id}-error`" class="text-sm text-error">{{ error }}</p>
  </div>
</template>
