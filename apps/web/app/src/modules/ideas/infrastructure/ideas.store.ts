import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@modules/auth'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import type {
  CreateIdeaInput,
  Idea,
  IdeaColumn,
  MoveIdeaInput,
  UpdateIdeaInput,
} from '@modules/ideas/domain'

const DEFAULT_COLUMNS: IdeaColumn[] = [
  { id: 'raw', name: 'Raw', order: 0 },
  { id: 'in-progress', name: 'In Progress', order: 1 },
  { id: 'done', name: 'Done', order: 2 },
]

function isIdeaArray(payload: unknown): payload is Idea[] {
  return Array.isArray(payload)
}

function isColumnArray(payload: unknown): payload is IdeaColumn[] {
  return Array.isArray(payload)
}

function readIdeas(payload: unknown): Idea[] {
  if (isIdeaArray(payload)) return payload

  if (
    payload &&
    typeof payload === 'object' &&
    'ideas' in payload &&
    isIdeaArray((payload as { ideas: unknown }).ideas)
  ) {
    return (payload as { ideas: Idea[] }).ideas
  }

  if (
    payload &&
    typeof payload === 'object' &&
    'columns' in payload &&
    Array.isArray((payload as { columns: unknown[] }).columns)
  ) {
    const columns = (
      payload as {
        columns: Array<{ id: string; ideas?: Idea[] }>
      }
    ).columns
    return columns.flatMap((column) =>
      (column.ideas ?? []).map((idea, index) => ({
        ...idea,
        columnId: idea.columnId || column.id,
        orderInColumn: Number.isFinite(idea.orderInColumn) ? idea.orderInColumn : index,
      })),
    )
  }

  return []
}

function readColumns(payload: unknown): IdeaColumn[] {
  if (isColumnArray(payload)) return payload

  if (
    payload &&
    typeof payload === 'object' &&
    'columns' in payload &&
    isColumnArray((payload as { columns: unknown }).columns)
  ) {
    return (payload as { columns: IdeaColumn[] }).columns
  }

  return []
}

export function reorderWithinList<T>(list: T[], fromIndex: number, toIndex: number): T[] {
  if (fromIndex === toIndex) {
    return [...list]
  }

  const next = [...list]
  const [moved] = next.splice(fromIndex, 1)
  if (!moved) {
    return [...list]
  }

  next.splice(toIndex, 0, moved)
  return next
}

export function normalizeColumnOrder(columns: IdeaColumn[]): IdeaColumn[] {
  return [...columns]
    .sort((a, b) => a.order - b.order)
    .map((column, index) => ({ ...column, order: index }))
}

export function normalizeIdeas(ideas: Idea[]): Idea[] {
  const byColumn = new Map<string, Idea[]>()

  for (const idea of ideas) {
    const list = byColumn.get(idea.columnId) ?? []
    list.push(idea)
    byColumn.set(idea.columnId, list)
  }

  const normalized: Idea[] = []

  for (const [columnId, list] of byColumn.entries()) {
    const ordered = [...list].sort((a, b) => a.orderInColumn - b.orderInColumn)
    for (const [index, idea] of ordered.entries()) {
      normalized.push({
        ...idea,
        columnId,
        orderInColumn: index,
      })
    }
  }

  return normalized
}

export const useIdeasStore = defineStore('ideas', () => {
  const auth = useAuthStore()
  const workspace = useWorkspaceStore()

  const ideas = ref<Idea[]>([])
  const columns = ref<IdeaColumn[]>([...DEFAULT_COLUMNS])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)

  const orderedColumns = computed(() => normalizeColumnOrder(columns.value))

  const ideasByColumn = computed<Record<string, Idea[]>>(() => {
    const grouped: Record<string, Idea[]> = {}

    for (const column of orderedColumns.value) {
      grouped[column.id] = []
    }

    for (const idea of normalizeIdeas(ideas.value)) {
      grouped[idea.columnId] = grouped[idea.columnId] ?? []
      grouped[idea.columnId]?.push(idea)
    }

    return grouped
  })

  const hasWorkspace = computed(() => Boolean(workspace.activeWorkspaceId))

  function requireWorkspace(): string {
    const workspaceId = workspace.activeWorkspaceId
    if (!workspaceId) {
      throw new Error('Workspace context is required to manage ideas.')
    }
    return workspaceId
  }

  async function loadBoard(): Promise<void> {
    if (!auth.isAuthenticated || !workspace.activeWorkspaceId) {
      return
    }

    loading.value = true
    error.value = null

    try {
      const [columnsResponse, ideasResponse] = await Promise.all([
        auth.apiFetch<unknown>('/api/ideas/columns', {
          method: 'GET',
          workspaceScoped: true,
        }),
        auth.apiFetch<unknown>('/api/ideas', {
          method: 'GET',
          workspaceScoped: true,
        }),
      ])

      const nextColumns = readColumns(columnsResponse)
      columns.value = normalizeColumnOrder(nextColumns.length ? nextColumns : DEFAULT_COLUMNS)
      ideas.value = normalizeIdeas(readIdeas(ideasResponse))
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Unable to load ideas board.'
    } finally {
      loading.value = false
    }
  }

  async function createIdea(input: CreateIdeaInput): Promise<Idea> {
    const workspaceId = requireWorkspace()
    const targetColumnId = input.columnId ?? orderedColumns.value[0]?.id ?? DEFAULT_COLUMNS[0].id
    const nextOrder = ideasByColumn.value[targetColumnId]?.length ?? 0

    const payload = {
      title: input.title.trim(),
      notes: input.notes ?? null,
      tags: input.tags ?? [],
      links: input.links ?? [],
      columnId: targetColumnId,
      orderInColumn: nextOrder,
    }

    saving.value = true
    error.value = null

    try {
      const created = await auth.apiFetch<Idea>('/api/ideas', {
        method: 'POST',
        workspaceScoped: true,
        body: JSON.stringify(payload),
      })

      const idea: Idea = {
        ...created,
        workspaceId: created.workspaceId || workspaceId,
        columnId: created.columnId || targetColumnId,
        orderInColumn: Number.isFinite(created.orderInColumn) ? created.orderInColumn : nextOrder,
        tags: created.tags ?? [],
        links: created.links ?? [],
      }

      ideas.value = normalizeIdeas([...ideas.value, idea])
      return idea
    } finally {
      saving.value = false
    }
  }

  async function updateIdea(ideaId: string, input: UpdateIdeaInput): Promise<Idea> {
    requireWorkspace()

    saving.value = true
    error.value = null

    try {
      const updated = await auth.apiFetch<Idea>(`/api/ideas/${ideaId}`, {
        method: 'PATCH',
        workspaceScoped: true,
        body: JSON.stringify(input),
      })

      ideas.value = normalizeIdeas(
        ideas.value.map((idea) => (idea.id === ideaId ? { ...idea, ...updated } : idea)),
      )

      const next = ideas.value.find((idea) => idea.id === ideaId)
      if (!next) {
        throw new Error('Idea was updated but could not be found in local state.')
      }
      return next
    } finally {
      saving.value = false
    }
  }

  async function moveIdea(ideaId: string, input: MoveIdeaInput): Promise<void> {
    requireWorkspace()

    const current = ideas.value.find((idea) => idea.id === ideaId)
    if (!current) {
      return
    }

    const sourceColumnIdeas = [...(ideasByColumn.value[current.columnId] ?? [])]
    const targetColumnIdeas =
      current.columnId === input.columnId
        ? sourceColumnIdeas
        : [...(ideasByColumn.value[input.columnId] ?? [])]

    const sourceIndex = sourceColumnIdeas.findIndex((item) => item.id === ideaId)
    if (sourceIndex < 0) {
      return
    }

    let nextIdeas = [...ideas.value]
    nextIdeas = nextIdeas.filter((idea) => idea.id !== ideaId)

    const movedIdea: Idea = {
      ...current,
      columnId: input.columnId,
      orderInColumn: input.orderInColumn,
      updatedAt: new Date().toISOString(),
    }

    if (current.columnId === input.columnId) {
      const reordered = reorderWithinList(sourceColumnIdeas, sourceIndex, input.orderInColumn)
      const merged = reordered.map((idea, index) => {
        if (idea.id === ideaId) {
          return { ...movedIdea, orderInColumn: index }
        }
        return { ...idea, orderInColumn: index }
      })

      nextIdeas = [...nextIdeas.filter((idea) => idea.columnId !== current.columnId), ...merged]
    } else {
      const sourceWithout = sourceColumnIdeas
        .filter((idea) => idea.id !== ideaId)
        .map((idea, index) => ({ ...idea, orderInColumn: index }))

      const insertIndex = Math.max(0, Math.min(input.orderInColumn, targetColumnIdeas.length))
      const targetWithMoved = [...targetColumnIdeas]
      targetWithMoved.splice(insertIndex, 0, movedIdea)

      const normalizedTarget = targetWithMoved.map((idea, index) => ({
        ...idea,
        columnId: input.columnId,
        orderInColumn: index,
      }))

      nextIdeas = [
        ...nextIdeas.filter(
          (idea) => idea.columnId !== current.columnId && idea.columnId !== input.columnId,
        ),
        ...sourceWithout,
        ...normalizedTarget,
      ]
    }

    const snapshot = [...ideas.value]
    ideas.value = normalizeIdeas(nextIdeas)

    try {
      await auth.apiFetch(`/api/ideas/${ideaId}/move`, {
        method: 'PATCH',
        workspaceScoped: true,
        body: JSON.stringify({ columnId: input.columnId, orderInColumn: input.orderInColumn }),
      })
    } catch (err) {
      ideas.value = snapshot
      error.value = err instanceof Error ? err.message : 'Unable to move idea.'
    }
  }

  async function deleteIdea(ideaId: string): Promise<void> {
    requireWorkspace()

    saving.value = true
    error.value = null

    try {
      await auth.apiFetch(`/api/ideas/${ideaId}`, {
        method: 'DELETE',
        workspaceScoped: true,
      })

      ideas.value = normalizeIdeas(ideas.value.filter((idea) => idea.id !== ideaId))
    } finally {
      saving.value = false
    }
  }

  async function convertIdea(ideaId: string): Promise<string | null> {
    requireWorkspace()

    saving.value = true
    error.value = null

    try {
      const result = await auth.apiFetch<{ publicationId?: string | null }>(
        `/api/ideas/${ideaId}/convert`,
        {
          method: 'POST',
          workspaceScoped: true,
        },
      )

      const publicationId = result.publicationId ?? null
      ideas.value = ideas.value.map((idea) =>
        idea.id === ideaId
          ? {
              ...idea,
              convertedToPublicationId: publicationId,
              updatedAt: new Date().toISOString(),
            }
          : idea,
      )

      return publicationId
    } finally {
      saving.value = false
    }
  }

  async function updateColumns(nextColumns: IdeaColumn[]): Promise<void> {
    requireWorkspace()

    const normalizedColumns = normalizeColumnOrder(nextColumns)
    if (!normalizedColumns.length) {
      throw new Error('At least one column is required.')
    }

    saving.value = true
    error.value = null

    try {
      await auth.apiFetch('/api/ideas/columns', {
        method: 'PUT',
        workspaceScoped: true,
        body: JSON.stringify({ columns: normalizedColumns }),
      })

      columns.value = normalizedColumns

      const validColumnIds = new Set(normalizedColumns.map((column) => column.id))
      const fallbackColumnId = normalizedColumns[0]?.id ?? DEFAULT_COLUMNS[0].id

      ideas.value = normalizeIdeas(
        ideas.value.map((idea) =>
          validColumnIds.has(idea.columnId)
            ? idea
            : {
                ...idea,
                columnId: fallbackColumnId,
              },
        ),
      )
    } finally {
      saving.value = false
    }
  }

  function createLocalColumn(name: string, color?: string | null): IdeaColumn {
    const maxOrder = orderedColumns.value.length
    return {
      id: `col-${crypto.randomUUID()}`,
      name: name.trim(),
      color: color ?? null,
      order: maxOrder,
    }
  }

  function clearState(): void {
    ideas.value = []
    columns.value = [...DEFAULT_COLUMNS]
    error.value = null
    loading.value = false
    saving.value = false
  }

  return {
    ideas,
    columns,
    orderedColumns,
    ideasByColumn,
    hasWorkspace,
    loading,
    saving,
    error,
    loadBoard,
    createIdea,
    updateIdea,
    moveIdea,
    deleteIdea,
    convertIdea,
    updateColumns,
    createLocalColumn,
    clearState,
  }
})
