<script setup lang="ts">
interface Props {
  modelValue: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>

<template>
  <div class="space-y-2">
    <label for="create-post-first-comment" class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
      {{ $t('composer.firstComment') }}
    </label>
    <input
      id="create-post-first-comment"
      :value="modelValue"
      type="text"
      :placeholder="$t('composer.firstCommentPlaceholder')"
      class="w-full bg-bg-primary border border-border-visible rounded-xl px-4 py-2.5 text-xs text-text-body placeholder:text-text-secondary focus:outline-none focus:border-text-display font-sans"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </div>
</template>
