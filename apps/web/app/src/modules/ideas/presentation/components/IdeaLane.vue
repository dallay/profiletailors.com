<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
  <Card
    data-testid="idea-lane"
    class="flex min-w-[280px] w-[300px] max-w-[320px] shrink-0 flex-col"
  >
    <CardHeader data-testid="idea-lane-header" class="sticky top-0 z-10 bg-bg-surface pb-3">
      <div class="flex items-center justify-between">
        <CardTitle class="text-sm font-medium text-text-display">
          {{ column.name }}
        </CardTitle>
        <Badge variant="outline">{{ ideas.length }}</Badge>
      </div>
    </CardHeader>

    <CardContent class="flex flex-1 flex-col space-y-3">
      <div
        :ref="(el) => setColumn(el as Element | null)"
        :data-dnd-column="column.id"
        data-testid="idea-lane-dropzone"
        class="min-h-30 flex-1"
      >
        <div
          data-testid="idea-lane-scroll"
          class="max-h-[60vh] space-y-3 overflow-y-auto pr-1"
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
            class="rounded-lg border border-dashed border-border-visible p-3 text-xs text-text-secondary"
          >
            {{ t('ideas.emptyColumn') }}
          </p>
        </div>
      </div>

      <Button
        variant="ghost"
        data-testid="idea-lane-add"
        class="w-full"
        @click="onAdd(column.id)"
      >
        <Plus class="mr-2 size-4" />
        Add idea
      </Button>
    </CardContent>
  </Card>
</template>
