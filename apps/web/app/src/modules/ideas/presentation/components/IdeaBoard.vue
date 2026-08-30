<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IdeaLane from './IdeaLane.vue'
import IdeaCard from './IdeaCard.vue'
import { Skeleton } from '@/components/ui/skeleton'
import { Plus } from '@lucide/vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

type ViewMode = 'board' | 'gallery'

const props = withDefaults(
  defineProps<{
    columns: IdeaColumn[]
    ideasByColumn: Record<string, Idea[]>
    loading: boolean
    viewMode?: ViewMode
    draggedIdeaId?: string | null
    setColumnRef?: (id: string, el: Element | null) => void
    setCardRef?: (id: string, el: Element | null) => void
  }>(),
  {
    viewMode: 'board',
    draggedIdeaId: null,
    setColumnRef: undefined,
    setCardRef: undefined,
  },
)

const { t } = useI18n()

const emit = defineEmits<{
  addIdea: [columnId: string]
  selectIdea: [ideaId: string]
  newColumn: []
}>()

function onAdd(columnId: string) {
  emit('addIdea', columnId)
}

function onSelect(ideaId: string) {
  emit('selectIdea', ideaId)
}

function onNewColumn() {
  emit('newColumn')
}
</script>

<template>
  <div data-testid="idea-board" class="w-full">
    <div v-if="loading" data-testid="idea-board-skeleton" class="overflow-x-auto pb-2">
      <div class="flex gap-4">
        <div
          v-for="i in 3"
          :key="i"
          data-testid="idea-board-skeleton-lane"
          class="min-w-[280px] w-[300px] max-w-[320px] shrink-0 animate-pulse space-y-3 rounded-xl border border-border-visible bg-bg-surface p-3"
        >
          <div class="flex items-center justify-between">
            <Skeleton class="h-4 w-24" />
            <Skeleton class="h-5 w-8" />
          </div>
          <Skeleton class="h-20 w-full" />
          <Skeleton class="h-20 w-full" />
          <Skeleton class="h-9 w-full" />
        </div>
      </div>
    </div>

    <div v-else data-testid="idea-board-scroll" class="overflow-x-auto pb-2">
      <div v-if="props.viewMode === 'board'" class="flex min-w-max items-start gap-3">
        <IdeaLane
          v-for="column in columns"
          :key="column.id"
          :column="column"
          :ideas="ideasByColumn[column.id] ?? []"
          :dragged-idea-id="props.draggedIdeaId"
          :set-column-ref="props.setColumnRef"
          :set-card-ref="props.setCardRef"
          @add="onAdd"
          @select-idea="onSelect"
        />
        <button
          type="button"
          data-testid="ideas-new-column"
          class="flex min-h-[460px] w-[280px] shrink-0 items-start justify-center gap-2 rounded-xl border border-dashed border-border-visible bg-bg-primary/20 px-4 py-8 text-sm text-text-secondary transition hover:border-text-secondary hover:text-text-display"
          @click="onNewColumn"
        >
          <Plus class="size-4" />
          {{ t('ideas.newGroup') }}
        </button>
      </div>

      <div v-else class="grid min-w-[720px] grid-cols-[repeat(auto-fit,minmax(280px,1fr))] items-start gap-4">
        <section
          v-for="column in columns"
          :key="column.id"
          :data-testid="`idea-gallery-column-${column.id}`"
          class="rounded-xl border border-border-visible bg-bg-surface/70 p-3"
        >
          <header class="flex items-center justify-between border-b border-border-subtle px-1 pb-3">
            <div class="flex items-center gap-2">
              <span
                class="size-2 rounded-full bg-text-secondary"
                :style="column.color ? { backgroundColor: column.color } : undefined"
                aria-hidden="true"
              />
              <h3 class="text-sm font-medium text-text-display">{{ column.name }}</h3>
            </div>
            <span class="font-mono text-[10px] text-text-secondary">{{ ideasByColumn[column.id]?.length ?? 0 }}</span>
          </header>
          <div class="grid gap-3 pt-3">
            <IdeaCard
              v-for="idea in ideasByColumn[column.id] ?? []"
              :key="idea.id"
              :idea="idea"
              :dragged-idea-id="props.draggedIdeaId"
              :set-card-ref="props.setCardRef"
              @select="onSelect"
            />
            <button
              v-if="(ideasByColumn[column.id]?.length ?? 0) === 0"
              type="button"
              data-testid="idea-gallery-empty"
              class="rounded-lg border border-dashed border-border-visible px-3 py-5 text-center text-xs text-text-secondary"
              @click="onAdd(column.id)"
            >
              {{ t('ideas.emptyColumn') }}
            </button>
            <button
              type="button"
              data-testid="idea-gallery-add"
              class="flex min-h-10 items-center justify-center gap-2 rounded-lg border border-dashed border-border-visible text-xs text-text-secondary transition hover:border-text-secondary hover:text-text-display"
              @click="onAdd(column.id)"
            >
              <Plus class="size-3.5" />
              {{ t('ideas.column.addIdea') }}
            </button>
          </div>
        </section>
        <button
          type="button"
          data-testid="ideas-gallery-new-column"
          class="flex min-h-48 items-start justify-center gap-2 rounded-xl border border-dashed border-border-visible bg-bg-primary/20 px-4 py-8 text-sm text-text-secondary transition hover:border-text-secondary hover:text-text-display"
          @click="onNewColumn"
        >
          <Plus class="size-4" />
          {{ t('ideas.newGroup') }}
        </button>
      </div>
    </div>
  </div>
</template>
