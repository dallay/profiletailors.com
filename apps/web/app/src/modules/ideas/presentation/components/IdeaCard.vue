<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Link, Tag } from '@lucide/vue'
import type { Idea } from '@modules/ideas/domain'

const props = withDefaults(
  defineProps<{ idea: Idea; draggedIdeaId?: string | null; setCardRef?: (id: string, el: Element | null) => void }>(),
  { draggedIdeaId: null, setCardRef: undefined },
)
const emit = defineEmits<{ select: [id: string] }>()

const { t } = useI18n()

const visibleTags = computed(() => props.idea.tags.slice(0, 3))
const overflowCount = computed(() => Math.max(0, props.idea.tags.length - 3))
const hasLinks = computed(() => props.idea.links.length > 0)
const isConverted = computed(() => Boolean(props.idea.convertedToPublicationId))
const isDragging = computed(() => props.draggedIdeaId === props.idea.id)

function onSelect() {
  emit('select', props.idea.id)
}

function setRef(el: Element | null) {
  props.setCardRef?.(props.idea.id, el)
}
</script>

<template>
  <button
    :ref="(el) => setRef(el as Element | null)"
    :data-dnd-draggable="props.idea.id"
    draggable="true"
    type="button"
    data-testid="idea-card"
    :class="[
      'group w-full rounded-lg border border-border-visible bg-bg-primary p-4 text-left transition duration-150 hover:-translate-y-px hover:border-text-secondary',
      isDragging ? 'opacity-50' : '',
    ]"
    @click="onSelect"
  >
    <div class="flex items-start justify-between gap-3">
      <p data-testid="idea-card-title" class="line-clamp-2 text-sm font-medium leading-5 text-text-display">
        {{ props.idea.title }}
      </p>
      <span
        class="mt-0.5 size-1.5 shrink-0 rounded-full bg-text-secondary opacity-0 transition group-hover:opacity-100"
        aria-hidden="true"
      />
    </div>

    <p
      v-if="props.idea.notes"
      data-testid="idea-card-notes"
      class="mt-2 line-clamp-2 text-xs leading-5 text-text-secondary"
    >
      {{ props.idea.notes }}
    </p>

    <div v-if="visibleTags.length" class="mt-3 flex flex-wrap gap-1">
      <span
        v-for="tag in visibleTags"
        :key="`${props.idea.id}-${tag}`"
        data-testid="idea-card-tag"
        class="inline-flex items-center gap-1 rounded-full border border-border-visible bg-bg-surface px-2 py-1 text-[10px] text-text-secondary"
      >
        <Tag class="size-3" />
        #{{ tag }}
      </span>
      <span v-if="overflowCount > 0" data-testid="idea-card-tags-overflow" class="text-xs text-text-secondary">
        +{{ overflowCount }}
      </span>
    </div>

    <div class="mt-4 flex items-center justify-between text-[11px] text-text-secondary">
      <span v-if="hasLinks" data-testid="idea-card-links" class="inline-flex items-center gap-1">
        <Link class="size-3.5" />
        🔗 {{ props.idea.links.length }}
      </span>
      <span v-else data-testid="idea-card-links-placeholder" class="hidden" />
      <span v-if="isConverted" data-testid="idea-card-converted" class="text-success">
        {{ t('ideas.card.converted') }}
      </span>
    </div>
  </button>
</template>
