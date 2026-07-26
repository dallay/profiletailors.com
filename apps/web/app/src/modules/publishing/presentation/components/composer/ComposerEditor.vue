<script setup lang="ts">
import { computed, ref } from 'vue'

interface Props {
  modelValue: string
  charLimit?: number
  placeholder?: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'dragover', event: DragEvent): void
  (e: 'dragleave', event: DragEvent): void
  (e: 'drop', event: DragEvent): void
  (e: 'paste', event: ClipboardEvent): void
  (e: 'emoji-click'): void
  (e: 'hashtag-click'): void
  (e: 'ai-assist-click'): void
}

const props = withDefaults(defineProps<Props>(), {
  charLimit: 3000,
})

const emit = defineEmits<Emits>()
const isAiProcessing = ref(false)

const charsRemaining = computed(() => props.charLimit - props.modelValue.length)
const isTextTooLong = computed(() => charsRemaining.value < 0)

function handleEmojiPicker() {
  emit('emoji-click')
}

function appendHashtag() {
  const tag = prompt('Enter tag (e.g. #socialmedia):')
  if (tag) {
    const formatted = tag.startsWith('#') ? tag : `#${tag}`
    emit('update:modelValue', props.modelValue ? `${props.modelValue} ${formatted}` : formatted)
  }
}

function handleAiAssist() {
  emit('ai-assist-click')
}
</script>

<template>
  <div class="flex flex-1 flex-col rounded-[24px] border border-border-visible bg-bg-primary/70 min-h-[420px]">
    <label for="create-post-text" class="sr-only">Post content</label>
    <textarea
      id="create-post-text"
      :value="modelValue"
      :placeholder="placeholder || $t('composer.placeholder')"
      class="min-h-[260px] w-full flex-1 resize-none bg-transparent p-5 text-sm text-text-body placeholder:text-text-secondary focus:outline-none font-sans"
      data-testid="composer-textarea"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      @dragover="emit('dragover', $event)"
      @dragleave="emit('dragleave', $event)"
      @drop="emit('drop', $event)"
      @paste="emit('paste', $event)"
    ></textarea>

    <!-- Toolbar -->
    <div class="flex items-center justify-between px-4 py-3 border-t border-border-visible bg-bg-primary/50">
      <div class="flex items-center gap-2">
        <button
          type="button"
          @click="handleEmojiPicker"
          class="p-1.5 rounded-lg hover:bg-bg-secondary transition-colors text-text-secondary hover:text-text-display"
          :title="$t('composer.emojiPicker')"
          aria-label="Add emoji"
        >
          😊
        </button>
        <button
          type="button"
          @click="appendHashtag"
          class="p-1.5 rounded-lg hover:bg-bg-secondary transition-colors text-text-secondary hover:text-text-display"
          :title="$t('composer.hashtag')"
          aria-label="Add hashtag"
        >
          #
        </button>
        <button
          type="button"
          @click="handleAiAssist"
          :disabled="isAiProcessing"
          class="p-1.5 rounded-lg hover:bg-bg-secondary transition-colors text-text-secondary hover:text-text-display disabled:opacity-50"
          :title="$t('composer.aiAssist')"
          aria-label="AI assist"
        >
          ✨
        </button>
      </div>

      <div class="text-right">
        <span
          class="text-xs font-medium"
          :class="[isTextTooLong ? 'text-error' : 'text-text-secondary']"
        >
          {{ charsRemaining }} / {{ charLimit }}
        </span>
      </div>
    </div>
  </div>
</template>
