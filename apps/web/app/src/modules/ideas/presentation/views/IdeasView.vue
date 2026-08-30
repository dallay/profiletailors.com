<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  Plus,
  Settings,
  ArrowUp,
  ArrowDown,
  Lightbulb,
  Trash2,
} from '@lucide/vue'
import type { IdeaColumn } from '@modules/ideas/domain'
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

const columns = computed(() => ideasStore.orderedColumns)

const selectedIdea = computed(() =>
  ideasStore.ideas.find((idea) => idea.id === selectedIdeaId.value) ?? null,
)

const boardColumns = computed(() =>
  columns.value.map((column) => ({
    ...column,
    ideas: ideasStore.ideasByColumn[column.id] ?? [],
  })),
)

const { setColumnRef, setCardRef, draggedIdeaId } = useIdeaDragAndDrop({
  boardColumns: boardColumns as never,
  ideasStore: ideasStore as never,
})

watch(
  () => workspace.activeWorkspaceId,
  async (workspaceId) => {
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

onMounted(async () => {
  if (workspace.activeWorkspaceId) await ideasStore.loadBoard()
  try {
    await publishingStore.fetchChannels()
  } catch {
  }
})
</script>

<template>
  <section class="mx-auto w-full max-w-350 space-y-6" data-testid="ideas-view">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div class="space-y-1">
        <h2 class="flex items-center gap-2 text-2xl font-semibold text-text-display">
          <Lightbulb class="size-5 text-amber-400" />
          {{ t('ideas.title') }}
        </h2>
        <p class="text-sm text-text-secondary">
          {{ t('ideas.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-2">
        <Button variant="outline" @click="openColumnSettings">
          <Settings class="mr-2 size-4" />
          {{ t('ideas.columns.button') }}
        </Button>
        <Button data-testid="ideas-top-add" @click="openComposerCreate()">
          <Plus class="mr-2 size-4" />
          {{ t('ideas.quickCapture.button') }}
        </Button>
      </div>
    </header>

    <div v-if="!ideasStore.hasWorkspace" class="rounded-xl border border-border-visible bg-bg-surface p-6 text-sm text-text-secondary">
      {{ t('ideas.workspaceRequired') }}
    </div>

    <div v-else-if="ideasStore.error" class="rounded-xl border border-red-500/30 bg-red-500/10 p-6 text-sm text-red-400">
      {{ ideasStore.error }}
    </div>

    <IdeaBoard
      v-else
      :columns="columns"
      :ideas-by-column="ideasStore.ideasByColumn"
      :loading="ideasStore.loading"
      :dragged-idea-id="draggedIdeaId"
      :set-column-ref="setColumnRef"
      :set-card-ref="setCardRef"
      @add-idea="openComposerCreate"
      @select-idea="openComposerEdit"
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
