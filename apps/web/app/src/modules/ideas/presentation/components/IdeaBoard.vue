<script setup lang="ts">
import IdeaLane from './IdeaLane.vue'
import { Skeleton } from '@/components/ui/skeleton'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

const props = withDefaults(
  defineProps<{
    columns: IdeaColumn[]
    ideasByColumn: Record<string, Idea[]>
    loading: boolean
    draggedIdeaId?: string | null
    setColumnRef?: (id: string, el: Element | null) => void
    setCardRef?: (id: string, el: Element | null) => void
  }>(),
  { draggedIdeaId: null, setColumnRef: undefined, setCardRef: undefined },
)

const emit = defineEmits<{ addIdea: [columnId: string]; selectIdea: [ideaId: string] }>()

function onAdd(columnId: string) {
  emit('addIdea', columnId)
}

function onSelect(ideaId: string) {
  emit('selectIdea', ideaId)
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
      <div class="flex gap-4">
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
      </div>
    </div>
  </div>
</template>
