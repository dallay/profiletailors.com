<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
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
      'w-full rounded-xl border border-border-visible bg-bg-primary p-3 text-left transition hover:border-text-secondary',
      isDragging ? 'opacity-50' : '',
    ]"
    @click="onSelect"
  >
    <p data-testid="idea-card-title" class="line-clamp-2 text-sm font-medium text-text-display">
      {{ props.idea.title }}
    </p>

    <p
      v-if="props.idea.notes"
      data-testid="idea-card-notes"
      class="mt-2 line-clamp-2 text-xs text-text-secondary"
    >
      {{ props.idea.notes }}
    </p>

    <div v-if="visibleTags.length" class="mt-3 flex flex-wrap gap-1">
      <Badge
        v-for="tag in visibleTags"
        :key="`${props.idea.id}-${tag}`"
        variant="secondary"
        data-testid="idea-card-tag"
      >
        #{{ tag }}
      </Badge>
      <span v-if="overflowCount > 0" data-testid="idea-card-tags-overflow" class="text-xs text-text-secondary">
        +{{ overflowCount }}
      </span>
    </div>

    <div class="mt-3 flex items-center justify-between text-[11px] text-text-secondary">
      <span v-if="hasLinks" data-testid="idea-card-links">🔗 {{ props.idea.links.length }}</span>
      <span v-else data-testid="idea-card-links-placeholder" class="hidden" />
      <span v-if="isConverted" data-testid="idea-card-converted" class="text-emerald-500">
        {{ t('ideas.card.converted') }}
      </span>
    </div>
  </button>
</template>
