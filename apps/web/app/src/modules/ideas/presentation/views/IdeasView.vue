<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  Plus,
  Settings,
  ArrowUp,
  ArrowDown,
  Lightbulb,
  Trash2,
  ChevronDown,
  Grid2X2,
  LayoutGrid,
  Tag,
} from '@lucide/vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'
import { useIdeasStore } from '@modules/ideas/infrastructure/ideas.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import IdeaBoard from '@modules/ideas/presentation/components/IdeaBoard.vue'
import IdeaComposerModal from '@modules/ideas/presentation/components/IdeaComposerModal.vue'
import CreatePostModal from '@modules/publishing/presentation/components/CreatePostModal.vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useIdeaDragAndDrop } from '@modules/ideas/application/useIdeaDragAndDrop'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

type ColumnDraft = Omit<IdeaColumn, 'color'> & { color: string }

const { t } = useI18n()
const ideasStore = useIdeasStore()
const workspace = useWorkspaceStore()
const publishingStore = usePublishingStore()

const isComposerOpen = ref(false)
const selectedIdeaId = ref<string | null>(null)
const composerInitialColumnId = ref<string | null>(null)
const isPublishingOpen = ref(false)
const publishingPrefill = ref('')
const handoffIdeaId = ref<string | null>(null)

const isColumnSettingsOpen = ref(false)
const columnDraft = ref<ColumnDraft[]>([])
const newColumnName = ref('')
const newColumnColor = ref('')
const viewMode = ref<'board' | 'gallery'>('board')
const selectedTag = ref<string | null>(null)
const isTagFilterOpen = ref(false)

const columns = computed(() => ideasStore.orderedColumns)

const availableTags = computed(() =>
  [...new Set(ideasStore.ideas.flatMap((idea) => idea.tags))].sort((a, b) =>
    a.localeCompare(b),
  ),
)

const selectedIdea = computed(() =>
  ideasStore.ideas.find((idea) => idea.id === selectedIdeaId.value) ?? null,
)

const boardColumns = computed(() =>
  columns.value.map((column) => ({
    ...column,
    ideas: ideasStore.ideasByColumn[column.id] ?? [],
  })),
)

const visibleIdeasByColumn = computed<Record<string, Idea[]>>(() => {
  const visible: Record<string, Idea[]> = {}
  for (const column of columns.value) {
    const ideas = ideasStore.ideasByColumn[column.id] ?? []
    visible[column.id] = selectedTag.value
      ? ideas.filter((idea) => idea.tags.includes(selectedTag.value as string))
      : ideas
  }
  return visible
})

const { setColumnRef, setCardRef, draggedIdeaId } = useIdeaDragAndDrop({
  boardColumns: boardColumns as never,
  ideasStore: ideasStore as never,
})

watch(
  () => workspace.activeWorkspaceId,
  async (workspaceId) => {
    selectedTag.value = null
    isTagFilterOpen.value = false
    if (!workspaceId) {
      ideasStore.clearState()
      return
    }
    await ideasStore.loadBoard()
    try {
      await publishingStore.fetchChannels()
    } catch {
    }
  },
  { immediate: true },
)

function openComposerCreate(columnId?: string): void {
  isTagFilterOpen.value = false
  selectedIdeaId.value = null
  composerInitialColumnId.value = columnId ?? columns.value[0]?.id ?? null
  isComposerOpen.value = true
}

function openComposerEdit(ideaId: string): void {
  selectedIdeaId.value = ideaId
  composerInitialColumnId.value = null
  isComposerOpen.value = true
}

function handleComposerClose(): void {
  isComposerOpen.value = false
}

function handleComposerSaved(): void {
  selectedIdeaId.value = null
}

function handleComposerDeleted(): void {
  selectedIdeaId.value = null
}

function handleHandoff(payload: { ideaId: string; prefill: string }): void {
  handoffIdeaId.value = payload.ideaId
  publishingPrefill.value = payload.prefill
  isPublishingOpen.value = true
}

function handlePublishingClose(): void {
  isPublishingOpen.value = false
  publishingPrefill.value = ''
  handoffIdeaId.value = null
}

async function handlePublishingCreated(payload: unknown): Promise<void> {
  const publicationId = (payload as { publicationId?: string })?.publicationId
    ?? (typeof payload === 'string' ? payload : null)
  const ideaId = handoffIdeaId.value
  if (!ideaId || !publicationId) {
    handlePublishingClose()
    return
  }
  try {
    if (typeof (ideasStore as unknown as { associatePublication?: unknown }).associatePublication === 'function') {
      await (ideasStore as unknown as { associatePublication: (id: string, pubId: string) => Promise<unknown> }).associatePublication(ideaId, publicationId)
    } else {
      await ideasStore.updateIdea(ideaId, { convertedToPublicationId: publicationId } as never)
    }
    toast.success(t('ideas.toasts.convertedWithId', { id: publicationId }))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('ideas.toasts.genericError'))
  } finally {
    handlePublishingClose()
  }
}

function openColumnSettings(): void {
  isTagFilterOpen.value = false
  columnDraft.value = columns.value.map((column) => ({ ...column, color: column.color ?? '' }))
  newColumnName.value = ''
  newColumnColor.value = ''
  isColumnSettingsOpen.value = true
}

function moveColumn(index: number, direction: -1 | 1): void {
  const target = index + direction
  if (target < 0 || target >= columnDraft.value.length) return
  const next = [...columnDraft.value]
  const [moved] = next.splice(index, 1)
  if (!moved) return
  next.splice(target, 0, moved)
  columnDraft.value = next.map((column, i) => ({ ...column, order: i }))
}

function addColumn(): void {
  const name = newColumnName.value.trim()
  if (!name) return
  const color = newColumnColor.value.trim()
  columnDraft.value = [
    ...columnDraft.value,
    ideasStore.createLocalColumn(name, color || null),
  ].map((column, index) => ({ ...column, color: column.color ?? '', order: index }))
  newColumnName.value = ''
  newColumnColor.value = ''
}

function removeColumn(columnId: string): void {
  if (columnDraft.value.length <= 1) {
    toast.error(t('ideas.columns.minimumOne'))
    return
  }
  columnDraft.value = columnDraft.value
    .filter((column) => column.id !== columnId)
    .map((column, index) => ({ ...column, order: index }))
}

async function saveColumns(): Promise<void> {
  try {
    await ideasStore.updateColumns(columnDraft.value)
    isColumnSettingsOpen.value = false
    toast.success(t('ideas.toasts.columnsUpdated'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('ideas.toasts.genericError'))
  }
}

function selectTag(tag: string | null): void {
  selectedTag.value = tag
  isTagFilterOpen.value = false
}

function clearTagFilter(): void {
  selectTag(null)
}
</script>

<template>
  <section class="mx-auto flex w-full max-w-[1500px] flex-col gap-6" data-testid="ideas-view">
    <header class="flex flex-wrap items-start justify-between gap-5 border-b border-border-subtle pb-5">
      <div class="flex items-start gap-3">
        <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-border-visible bg-bg-surface text-text-display">
          <Lightbulb class="size-5" />
        </div>
        <div class="space-y-1">
          <h2 class="text-2xl font-medium tracking-tight text-text-display">
            {{ t('ideas.title') }}
          </h2>
          <p class="max-w-xl text-sm text-text-secondary">
            {{ t('ideas.subtitle') }}
          </p>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <Button
          variant="outline"
          class="h-10 rounded-lg px-3 text-xs"
          data-testid="ideas-columns-button"
          @click="openColumnSettings"
        >
          <Settings class="mr-2 size-4" />
          {{ t('ideas.columns.button') }}
        </Button>
        <Button
          data-testid="ideas-top-add"
          class="h-10 rounded-lg px-4 text-xs"
          @click="openComposerCreate()"
        >
          <Plus class="mr-2 size-4" />
          {{ t('ideas.quickCapture.button') }}
        </Button>
      </div>
    </header>

    <div class="flex flex-wrap items-center justify-between gap-3" data-testid="ideas-toolbar">
      <div class="relative">
        <button
          type="button"
          class="inline-flex min-h-10 items-center gap-2 rounded-lg border border-border-visible bg-bg-surface px-3 text-sm text-text-body transition hover:border-text-secondary"
          data-testid="ideas-tag-filter"
          :aria-expanded="isTagFilterOpen"
          aria-haspopup="listbox"
          @click="isTagFilterOpen = !isTagFilterOpen"
          @keydown.esc="isTagFilterOpen = false"
        >
          <Tag class="size-4 text-text-secondary" />
          <span>{{ selectedTag ? `#${selectedTag}` : t('ideas.filters.tags') }}</span>
          <ChevronDown class="size-3.5 text-text-secondary" />
        </button>
        <div
          v-if="isTagFilterOpen"
          class="absolute left-0 top-12 z-30 min-w-48 rounded-lg border border-border-visible bg-bg-surface p-1"
          data-testid="ideas-tag-menu"
          role="listbox"
          :aria-label="t('ideas.filters.tags')"
        >
          <button
            type="button"
            role="option"
            class="flex min-h-10 w-full items-center justify-between rounded-md px-3 text-left text-sm text-text-body hover:bg-bg-primary"
            :aria-selected="selectedTag === null"
            data-testid="ideas-tag-all"
            @click="clearTagFilter"
          >
            {{ t('ideas.filters.all') }}
            <span class="font-mono text-[10px] text-text-secondary">{{ ideasStore.ideas.length }}</span>
          </button>
          <button
            v-for="tag in availableTags"
            :key="tag"
            type="button"
            role="option"
            class="flex min-h-10 w-full items-center rounded-md px-3 text-left text-sm text-text-body hover:bg-bg-primary"
            :aria-selected="selectedTag === tag"
            :data-testid="`ideas-tag-${tag}`"
            @click="selectTag(tag)"
          >
            #{{ tag }}
          </button>
          <p v-if="availableTags.length === 0" class="px-3 py-2 text-xs text-text-secondary">
            {{ t('ideas.filters.empty') }}
          </p>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <span v-if="selectedTag" class="text-xs text-text-secondary">
          {{ t('ideas.filters.showing', { tag: `#${selectedTag}` }) }}
        </span>
        <fieldset class="flex rounded-lg border border-border-visible bg-bg-surface p-1">
          <legend class="sr-only">{{ t('ideas.view.label') }}</legend>
          <button
            type="button"
            class="inline-flex min-h-8 items-center gap-2 rounded-md px-3 text-xs transition"
            :class="viewMode === 'board' ? 'bg-bg-primary text-text-display' : 'text-text-secondary hover:text-text-body'"
            :aria-pressed="viewMode === 'board'"
            data-testid="ideas-view-board"
            @click="viewMode = 'board'"
          >
            <LayoutGrid class="size-3.5" />
            {{ t('ideas.view.board') }}
          </button>
          <button
            type="button"
            class="inline-flex min-h-8 items-center gap-2 rounded-md px-3 text-xs transition"
            :class="viewMode === 'gallery' ? 'bg-bg-primary text-text-display' : 'text-text-secondary hover:text-text-body'"
            :aria-pressed="viewMode === 'gallery'"
            data-testid="ideas-view-gallery"
            @click="viewMode = 'gallery'"
          >
            <Grid2X2 class="size-3.5" />
            {{ t('ideas.view.gallery') }}
          </button>
        </fieldset>
      </div>
    </div>

    <div v-if="!ideasStore.hasWorkspace" class="rounded-xl border border-border-visible bg-bg-surface p-6 text-sm text-text-secondary">
      {{ t('ideas.workspaceRequired') }}
    </div>

    <div v-else-if="ideasStore.error" class="rounded-xl border border-red-500/30 bg-red-500/10 p-6 text-sm text-red-400">
      {{ ideasStore.error }}
    </div>

    <IdeaBoard
      v-else
      :columns="columns"
      :ideas-by-column="visibleIdeasByColumn"
      :loading="ideasStore.loading"
      :view-mode="viewMode"
      :dragged-idea-id="draggedIdeaId"
      :set-column-ref="setColumnRef"
      :set-card-ref="setCardRef"
      @add-idea="openComposerCreate"
      @select-idea="openComposerEdit"
      @new-column="openColumnSettings"
    />

    <IdeaComposerModal
      :open="isComposerOpen"
      :idea="selectedIdea"
      :columns="columns"
      :initial-column-id="composerInitialColumnId"
      @update:open="(v) => (isComposerOpen = v)"
      @close="handleComposerClose"
      @saved="handleComposerSaved"
      @deleted="handleComposerDeleted"
      @handoff="handleHandoff"
    />

    <CreatePostModal
      :is-open="isPublishingOpen"
      :initial-content="publishingPrefill"
      @close="handlePublishingClose"
      @created="handlePublishingCreated"
    />

    <Dialog v-model:open="isColumnSettingsOpen">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{{ t('ideas.columns.title') }}</DialogTitle>
          <DialogDescription>{{ t('ideas.columns.description') }}</DialogDescription>
        </DialogHeader>

        <div class="space-y-3">
          <div
            v-for="(column, index) in columnDraft"
            :key="column.id"
            class="flex items-center gap-2 rounded-xl border border-border-visible bg-bg-primary p-2"
          >
            <label class="sr-only" :for="`idea-column-name-${column.id}`">{{ t('ideas.columns.columnName') }}</label>
            <Input :id="`idea-column-name-${column.id}`" v-model="column.name" class="flex-1" />
            <label class="sr-only" :for="`idea-column-color-${column.id}`">{{ t('ideas.columns.columnColor') }}</label>
            <Input :id="`idea-column-color-${column.id}`" v-model="column.color" class="w-28" :placeholder="t('ideas.columns.colorPlaceholder')" />
            <Button variant="ghost" size="icon-sm" :disabled="index === 0" @click="moveColumn(index, -1)"><ArrowUp class="size-4" /></Button>
            <Button variant="ghost" size="icon-sm" :disabled="index === columnDraft.length - 1" @click="moveColumn(index, 1)"><ArrowDown class="size-4" /></Button>
            <Button variant="ghost" size="icon-sm" @click="removeColumn(column.id)"><Trash2 class="size-4" /></Button>
          </div>

          <div class="grid gap-2 rounded-xl border border-dashed border-border-visible p-3 sm:grid-cols-[1fr_120px_auto]">
            <label class="sr-only" for="idea-new-column-name">{{ t('ideas.columns.newColumnName') }}</label>
            <Input id="idea-new-column-name" v-model="newColumnName" :placeholder="t('ideas.columns.newNamePlaceholder')" />
            <label class="sr-only" for="idea-new-column-color">{{ t('ideas.columns.newColumnColor') }}</label>
            <Input id="idea-new-column-color" v-model="newColumnColor" :placeholder="t('ideas.columns.colorPlaceholder')" />
            <Button variant="outline" @click="addColumn"><Plus class="mr-2 size-4" />{{ t('ideas.columns.add') }}</Button>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="isColumnSettingsOpen = false">{{ t('common.cancel') }}</Button>
          <Button :disabled="columnDraft.length === 0 || ideasStore.saving" @click="saveColumns">{{ t('common.save') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
