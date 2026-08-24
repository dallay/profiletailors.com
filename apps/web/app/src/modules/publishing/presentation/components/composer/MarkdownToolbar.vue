<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  Bold,
  Italic,
  Strikethrough,
  Heading,
  List,
  ListOrdered,
  Quote,
  Link,
  Code,
} from '@lucide/vue'

interface Props {
  disabled?: boolean
}

withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'bold'): void
  (e: 'italic'): void
  (e: 'strikethrough'): void
  (e: 'heading'): void
  (e: 'unordered-list'): void
  (e: 'ordered-list'): void
  (e: 'blockquote'): void
  (e: 'link'): void
  (e: 'code'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div
    role="toolbar"
    :aria-label="t('composer.markdown.toolbarLabel')"
    class="flex flex-wrap items-center gap-0.5"
    data-testid="markdown-toolbar"
  >
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.bold')"
      :aria-label="t('composer.markdown.bold')"
      data-testid="md-bold"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('bold')"
    >
      <Bold class="size-4" />
    </button>
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.italic')"
      :aria-label="t('composer.markdown.italic')"
      data-testid="md-italic"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('italic')"
    >
      <Italic class="size-4" />
    </button>
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.strikethrough')"
      :aria-label="t('composer.markdown.strikethrough')"
      data-testid="md-strikethrough"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('strikethrough')"
    >
      <Strikethrough class="size-4" />
    </button>
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.code')"
      :aria-label="t('composer.markdown.code')"
      data-testid="md-code"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('code')"
    >
      <Code class="size-4" />
    </button>
    <span class="mx-1 h-5 w-px bg-border-subtle" aria-hidden="true" />
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.heading')"
      :aria-label="t('composer.markdown.heading')"
      data-testid="md-heading"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('heading')"
    >
      <Heading class="size-4" />
    </button>
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.unorderedList')"
      :aria-label="t('composer.markdown.unorderedList')"
      data-testid="md-unordered-list"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('unordered-list')"
    >
      <List class="size-4" />
    </button>
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.orderedList')"
      :aria-label="t('composer.markdown.orderedList')"
      data-testid="md-ordered-list"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('ordered-list')"
    >
      <ListOrdered class="size-4" />
    </button>
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.blockquote')"
      :aria-label="t('composer.markdown.blockquote')"
      data-testid="md-blockquote"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('blockquote')"
    >
      <Quote class="size-4" />
    </button>
    <span class="mx-1 h-5 w-px bg-border-subtle" aria-hidden="true" />
    <button
      type="button"
      :disabled="disabled"
      :title="t('composer.markdown.link')"
      :aria-label="t('composer.markdown.link')"
      data-testid="md-link"
      class="flex size-8 items-center justify-center rounded-lg text-text-secondary transition hover:bg-bg-surface hover:text-text-display disabled:opacity-40 disabled:hover:bg-transparent"
      @click="emit('link')"
    >
      <Link class="size-4" />
    </button>
  </div>
</template>
