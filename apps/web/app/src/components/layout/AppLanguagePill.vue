<script setup lang="ts">
type Locale = 'en' | 'es'

defineProps<{
  current: Locale
}>()

const emit = defineEmits<(e: 'change', locale: Locale) => void>()

const LOCALES: ReadonlyArray<{ value: Locale; label: string }> = [
  { value: 'en', label: 'EN' },
  { value: 'es', label: 'ES' },
]
</script>

<template>
  <div
    class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]"
    role="radiogroup"
    aria-label="Language"
  >
    <!-- biome-ignore lint/a11y/useSemanticElements: spec requires role="radio" on a button for the radiogroup pill design (R-A11Y-4) -->
    <button
      v-for="loc in LOCALES"
      :key="loc.value"
      type="button"
      role="radio"
      :aria-checked="current === loc.value ? 'true' : 'false'"
      class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
      :class="current === loc.value
        ? 'bg-text-display text-bg-primary'
        : 'text-text-secondary hover:text-text-display'"
      @click="emit('change', loc.value)"
    >
      {{ loc.label }}
    </button>
  </div>
</template>
