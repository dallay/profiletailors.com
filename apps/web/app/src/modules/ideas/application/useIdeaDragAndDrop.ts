import { ref, watch, nextTick, onBeforeUnmount, type Ref } from 'vue'
import {
  draggable,
  dropTargetForElements,
  monitorForElements,
  type ElementEventPayloadMap,
} from '@atlaskit/pragmatic-drag-and-drop/element/adapter'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

type BoardColumn = IdeaColumn & { ideas: Idea[] }

type DropTargetData = {
  kind: 'card' | 'column'
  columnId: string
  ideaId?: string
}

type DragData = {
  ideaId: string
  columnId: string
}

type DndDependencies = {
  monitorForElements: typeof monitorForElements
  draggable: typeof draggable
  dropTargetForElements: typeof dropTargetForElements
}

type UseIdeaDragAndDropOptions = {
  boardColumns: Ref<BoardColumn[]>
  ideasStore: {
    ideas: Idea[]
    ideasByColumn: Record<string, Idea[]>
    moveIdea: (id: string, input: { columnId: string; orderInColumn: number }) => Promise<void>
  }
  dependencies?: DndDependencies
}

export function useIdeaDragAndDrop(options: UseIdeaDragAndDropOptions) {
  const dnd = options.dependencies ?? { monitorForElements, draggable, dropTargetForElements }
  const columnElements = new Map<string, HTMLElement>()
  const cardElements = new Map<string, HTMLElement>()
  const draggedIdeaId = ref<string | null>(null)
  const cleanupFns = ref<Array<() => void>>([])

  function cleanupDragAndDrop() {
    for (const fn of cleanupFns.value) {
      fn()
    }
    cleanupFns.value = []
  }

  function setColumnRef(columnId: string, el: Element | null) {
    if (el instanceof HTMLElement) {
      columnElements.set(columnId, el)
      return
    }
    columnElements.delete(columnId)
  }

  function setCardRef(ideaId: string, el: Element | null) {
    if (el instanceof HTMLElement) {
      cardElements.set(ideaId, el)
      return
    }
    cardElements.delete(ideaId)
  }

  function findColumnIdeas(columnId: string): Idea[] {
    return options.ideasStore.ideasByColumn[columnId] ?? []
  }

  function findIdeaLocation(ideaId: string): { columnId: string; index: number } | null {
    for (const column of options.boardColumns.value) {
      const index = column.ideas.findIndex((idea) => idea.id === ideaId)
      if (index >= 0) {
        return { columnId: column.id, index }
      }
    }
    return null
  }

  function getDropTargetData(event: ElementEventPayloadMap['onDrop']): DropTargetData | null {
    for (const target of event.location.current.dropTargets) {
      const kind = target.data.kind
      const columnId = target.data.columnId
      if ((kind === 'card' || kind === 'column') && typeof columnId === 'string') {
        return {
          kind,
          columnId,
          ideaId: typeof target.data.ideaId === 'string' ? target.data.ideaId : undefined,
        }
      }
    }
    return null
  }

  function getDropIndex(target: DropTargetData, inputY: number): number {
    if (target.kind === 'column') {
      return findColumnIdeas(target.columnId).length
    }
    if (!target.ideaId) {
      return findColumnIdeas(target.columnId).length
    }
    const ideas = findColumnIdeas(target.columnId)
    const targetIndex = ideas.findIndex((idea) => idea.id === target.ideaId)
    if (targetIndex < 0) {
      return ideas.length
    }
    const targetElement = cardElements.get(target.ideaId)
    if (!targetElement) {
      return targetIndex
    }
    const rect = targetElement.getBoundingClientRect()
    const insertAfter = inputY >= rect.top + rect.height / 2
    return targetIndex + (insertAfter ? 1 : 0)
  }

  async function onDrop(event: ElementEventPayloadMap['onDrop']): Promise<void> {
    draggedIdeaId.value = null
    const sourceIdeaId = event.source.data.ideaId
    if (typeof sourceIdeaId !== 'string') {
      return
    }
    const sourceLocation = findIdeaLocation(sourceIdeaId)
    if (!sourceLocation) {
      return
    }
    const target = getDropTargetData(event)
    if (!target) {
      return
    }
    const targetIndex = getDropIndex(target, event.location.current.input.clientY)
    let normalizedIndex = targetIndex
    if (sourceLocation.columnId === target.columnId && sourceLocation.index < targetIndex) {
      normalizedIndex -= 1
    }
    if (sourceLocation.columnId === target.columnId && sourceLocation.index === normalizedIndex) {
      return
    }
    await options.ideasStore.moveIdea(sourceIdeaId, {
      columnId: target.columnId,
      orderInColumn: Math.max(0, normalizedIndex),
    })
  }

  function registerDragAndDrop() {
    cleanupDragAndDrop()
    const fns: Array<() => void> = []
    fns.push(
      dnd.monitorForElements({
        onDragStart: ({ source }) => {
          draggedIdeaId.value = typeof source.data.ideaId === 'string' ? source.data.ideaId : null
        },
        onDrop,
      }),
    )
    for (const column of options.boardColumns.value) {
      const columnElement = columnElements.get(column.id)
      if (columnElement) {
        fns.push(
          dnd.dropTargetForElements({
            element: columnElement,
            getData: () => ({
              kind: 'column',
              columnId: column.id,
            }),
          }),
        )
      }
      for (const idea of column.ideas) {
        const cardElement = cardElements.get(idea.id)
        if (!cardElement) {
          continue
        }
        fns.push(
          dnd.draggable({
            element: cardElement,
            getInitialData: (): DragData => ({
              ideaId: idea.id,
              columnId: column.id,
            }),
          }),
          dnd.dropTargetForElements({
            element: cardElement,
            getData: (): DropTargetData => ({
              kind: 'card',
              columnId: column.id,
              ideaId: idea.id,
            }),
          }),
        )
      }
    }
    cleanupFns.value = fns
  }

  watch(
    () =>
      options.boardColumns.value
        .map((c) => `${c.id}:${c.ideas.map((i) => i.id).join(',')}`)
        .join('|'),
    async () => {
      await nextTick()
      registerDragAndDrop()
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    cleanupDragAndDrop()
  })

  function unmount() {
    cleanupDragAndDrop()
  }

  return {
    columnElements,
    cardElements,
    draggedIdeaId,
    setColumnRef,
    setCardRef,
    getDropIndex,
    cleanupDragAndDrop,
    registerDragAndDrop,
    findIdeaLocation,
    findColumnIdeas,
    unmount,
  }
}
