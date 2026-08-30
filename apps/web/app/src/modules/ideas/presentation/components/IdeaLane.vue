<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Plus } from '@lucide/vue'
import IdeaCard from './IdeaCard.vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

const props = withDefaults(
  defineProps<{
    column: IdeaColumn
    ideas: Idea[]
    draggedIdeaId?: string | null
    setColumnRef?: (id: string, el: Element | null) => void
    setCardRef?: (id: string, el: Element | null) => void
  }>(),
  { draggedIdeaId: null, setColumnRef: undefined, setCardRef: undefined },
)
const emit = defineEmits<{ add: [columnId: string]; selectIdea: [ideaId: string] }>()

const { t } = useI18n()

function onAdd(columnId: string) {
  emit('add', columnId)
}

function onSelect(ideaId: string) {
  emit('selectIdea', ideaId)
}

function setColumn(el: Element | null) {
  props.setColumnRef?.(props.column.id, el)
}
</script>

<template>
  <article
    data-testid="idea-lane"
    class="flex min-h-[460px] min-w-[280px] w-[300px] max-w-[320px] shrink-0 flex-col overflow-hidden rounded-xl border border-border-visible bg-bg-surface/85"
  >
    <header data-testid="idea-lane-header" class="sticky top-0 z-10 border-b border-border-subtle bg-bg-surface px-4 py-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span
            class="size-2 rounded-full bg-text-secondary"
            :style="column.color ? { backgroundColor: column.color } : undefined"
            aria-hidden="true"
          />
          <h3 class="text-sm font-medium text-text-display">{{ column.name }}</h3>
        </div>
        <div class="flex items-center gap-2">
          <span class="rounded-full border border-border-visible px-2 py-0.5 font-mono text-[10px] text-text-secondary">
            {{ ideas.length }}
          </span>
          <button
            type="button"
            data-testid="idea-lane-header-add"
            class="flex size-7 items-center justify-center rounded-md text-text-secondary transition hover:bg-bg-primary hover:text-text-display"
            :aria-label="`${t('ideas.column.addIdea')} — ${column.name}`"
            @click="onAdd(column.id)"
          >
            <Plus class="size-3.5" />
          </button>
        </div>
      </div>
    </header>

    <div class="flex flex-1 flex-col px-3 py-3">
      <div
        :ref="(el) => setColumn(el as Element | null)"
        :data-dnd-column="column.id"
        data-testid="idea-lane-dropzone"
        class="min-h-30 flex-1"
      >
        <div
          data-testid="idea-lane-scroll"
          class="thin-scrollbar max-h-[calc(100vh-18rem)] space-y-3 overflow-y-auto pr-1"
        >
          <IdeaCard
            v-for="idea in ideas"
            :key="idea.id"
            :idea="idea"
            :dragged-idea-id="draggedIdeaId"
            :set-card-ref="setCardRef"
            @select="onSelect"
          />

          <p
            v-if="ideas.length === 0"
            data-testid="idea-lane-empty"
            class="rounded-lg border border-dashed border-border-visible px-3 py-6 text-center text-xs text-text-secondary"
          >
            {{ t('ideas.emptyColumn') }}
          </p>
        </div>
      </div>

      <button
        type="button"
        data-testid="idea-lane-add"
        class="mt-3 flex min-h-10 w-full items-center justify-center gap-2 rounded-lg border border-dashed border-border-visible text-xs text-text-secondary transition hover:border-text-secondary hover:text-text-display"
        @click="onAdd(column.id)"
      >
        <Plus class="size-3.5" />
        {{ t('ideas.column.addIdea') }}
      </button>
    </div>
  </article>
</template>
