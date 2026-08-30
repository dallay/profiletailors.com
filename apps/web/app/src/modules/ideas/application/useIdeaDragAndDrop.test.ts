import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useIdeaDragAndDrop } from './useIdeaDragAndDrop'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

function makeColumn(id: string, order: number): IdeaColumn {
  return { id, name: id, order }
}

function makeIdea(id: string, columnId: string, orderInColumn: number): Idea {
  return {
    id,
    workspaceId: 'ws-1',
    title: id,
    notes: null,
    tags: [],
    links: [],
    columnId,
    orderInColumn,
    convertedToPublicationId: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  }
}

type MonitorOptions = {
  onDragStart: (event: { source: { data: Record<string, unknown> } }) => void
  onDrop: (event: {
    source: { data: Record<string, unknown> }
    location: {
      current: { dropTargets: Array<{ data: Record<string, unknown> }>; input: { clientY: number } }
    }
  }) => Promise<void>
}

describe('useIdeaDragAndDrop', () => {
  let monitorOptions: unknown[]
  let draggableOptions: unknown[]
  let dropTargetOptions: unknown[]
  let cleanups: Array<ReturnType<typeof vi.fn>>
  let mockDnd: {
    monitorForElements: ReturnType<typeof vi.fn>
    draggable: ReturnType<typeof vi.fn>
    dropTargetForElements: ReturnType<typeof vi.fn>
  }
  let ideasStore: {
    ideas: Idea[]
    ideasByColumn: Record<string, Idea[]>
    moveIdea: ReturnType<typeof vi.fn>
  }

  beforeEach(() => {
    monitorOptions = []
    draggableOptions = []
    dropTargetOptions = []
    cleanups = []
    const makeCleanup = () => {
      const fn = vi.fn()
      cleanups.push(fn)
      return fn
    }
    mockDnd = {
      draggable: vi.fn((opts: unknown) => {
        draggableOptions.push(opts)
        return makeCleanup()
      }),
      dropTargetForElements: vi.fn((opts: unknown) => {
        dropTargetOptions.push(opts)
        return makeCleanup()
      }),
      monitorForElements: vi.fn((opts: unknown) => {
        monitorOptions.push(opts)
        return makeCleanup()
      }),
    }

    const first = makeIdea('idea-1', 'raw', 0)
    const second = makeIdea('idea-2', 'raw', 1)
    const third = makeIdea('idea-3', 'done', 0)
    ideasStore = {
      ideas: [first, second, third],
      ideasByColumn: { raw: [first, second], done: [third] },
      moveIdea: vi.fn().mockResolvedValue(undefined),
    }
  })

  function latestMonitor(): MonitorOptions {
    return monitorOptions.at(-1) as MonitorOptions
  }

  function dropEvent(
    sourceData: Record<string, unknown>,
    dropTargets: Array<{ data: Record<string, unknown> }>,
    clientY = 0,
  ): Parameters<MonitorOptions['onDrop']>[0] {
    return {
      source: { data: sourceData },
      location: { current: { dropTargets, input: { clientY } } },
    }
  }

  it('registers drag sources and drop targets for columns and cards', async () => {
    const boardColumns = ref([
      { ...makeColumn('raw', 0), ideas: [makeIdea('idea-1', 'raw', 0), makeIdea('idea-2', 'raw', 1)] },
      { ...makeColumn('done', 1), ideas: [makeIdea('idea-3', 'done', 0)] },
    ])
    const { setColumnRef, setCardRef } = useIdeaDragAndDrop({
      boardColumns: boardColumns as never,
      ideasStore: ideasStore as never,
      dependencies: mockDnd as never,
    })

    const colEl = document.createElement('div')
    const cardEl1 = document.createElement('div')
    const cardEl2 = document.createElement('div')
    const cardEl3 = document.createElement('div')

    setColumnRef('raw', colEl)
    setColumnRef('done', document.createElement('div'))
    setCardRef('idea-1', cardEl1)
    setCardRef('idea-2', cardEl2)
    setCardRef('idea-3', cardEl3)

    await nextTick()
    await new Promise((r) => setTimeout(r, 0))

    expect(mockDnd.monitorForElements).toHaveBeenCalledOnce()
    expect(mockDnd.draggable).toHaveBeenCalled()
    expect(mockDnd.dropTargetForElements).toHaveBeenCalled()

    const draggable = draggableOptions
      .map((o) => o as { getInitialData: () => Record<string, unknown> })
      .find((o) => o.getInitialData().ideaId === 'idea-2')
    expect(draggable?.getInitialData()).toEqual({ ideaId: 'idea-2', columnId: 'raw' })

    const doneColumn = dropTargetOptions
      .map((o) => o as { getData: () => Record<string, unknown> })
      .find((o) => o.getData().kind === 'column' && o.getData().columnId === 'done')
    expect(doneColumn?.getData()).toEqual({ kind: 'column', columnId: 'done' })
  })

  it('computes drop index via half-height and moves within column', async () => {
    const first = makeIdea('idea-1', 'raw', 0)
    const second = makeIdea('idea-2', 'raw', 1)
    const boardColumns = ref([{ ...makeColumn('raw', 0), ideas: [first, second] }])
    const { setCardRef, setColumnRef, getDropIndex } = useIdeaDragAndDrop({
      boardColumns: boardColumns as never,
      ideasStore: { ideas: [first, second], ideasByColumn: { raw: [first, second] }, moveIdea: ideasStore.moveIdea } as never,
      dependencies: mockDnd as never,
    })

    const cardEl1 = document.createElement('div')
    vi.spyOn(cardEl1, 'getBoundingClientRect').mockReturnValue({ top: 100, height: 40, left: 0, right: 0, bottom: 140, width: 0, x: 0, y: 0, toJSON: () => {} } as DOMRect)
    const cardEl2 = document.createElement('div')
    vi.spyOn(cardEl2, 'getBoundingClientRect').mockReturnValue({ top: 100, height: 40, left: 0, right: 0, bottom: 140, width: 0, x: 0, y: 0, toJSON: () => {} } as DOMRect)
    setCardRef('idea-1', cardEl1)
    setCardRef('idea-2', cardEl2)
    setColumnRef('raw', document.createElement('div'))

    await nextTick()
    await new Promise((r) => setTimeout(r, 0))

    expect(getDropIndex({ kind: 'card', columnId: 'raw', ideaId: 'idea-2' }, 90)).toBe(1)
    expect(getDropIndex({ kind: 'card', columnId: 'raw', ideaId: 'idea-2' }, 130)).toBe(2)

    const monitor = latestMonitor()
    monitor.onDragStart({ source: { data: { ideaId: 'idea-2' } } })
    await monitor.onDrop(
      dropEvent({ ideaId: 'idea-2' }, [{ data: { kind: 'card', columnId: 'raw', ideaId: 'idea-1' } }], 90),
    )
    expect(ideasStore.moveIdea).toHaveBeenCalledWith('idea-2', { columnId: 'raw', orderInColumn: 0 })

    ideasStore.moveIdea.mockClear()
    monitor.onDragStart({ source: { data: { ideaId: 'idea-1' } } })
    await monitor.onDrop(
      dropEvent({ ideaId: 'idea-1' }, [{ data: { kind: 'card', columnId: 'raw', ideaId: 'idea-2' } }], 130),
    )
    expect(ideasStore.moveIdea).toHaveBeenCalledWith('idea-1', { columnId: 'raw', orderInColumn: 1 })
  })

  it('moves across columns to end when dropping on column', async () => {
    const first = makeIdea('idea-1', 'raw', 0)
    const second = makeIdea('idea-2', 'done', 0)
    const boardColumns = ref([
      { ...makeColumn('raw', 0), ideas: [first] },
      { ...makeColumn('done', 1), ideas: [second] },
    ])
    useIdeaDragAndDrop({
      boardColumns: boardColumns as never,
      ideasStore: { ideas: [first, second], ideasByColumn: { raw: [first], done: [second] }, moveIdea: ideasStore.moveIdea } as never,
      dependencies: mockDnd as never,
    })
    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    const monitor = latestMonitor()
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, [{ data: { kind: 'column', columnId: 'done' } }]))
    expect(ideasStore.moveIdea).toHaveBeenCalledWith('idea-1', { columnId: 'done', orderInColumn: 1 })
  })

  it('ignores invalid and no-op drops', async () => {
    const first = makeIdea('idea-1', 'raw', 0)
    const second = makeIdea('idea-2', 'raw', 1)
    const boardColumns = ref([{ ...makeColumn('raw', 0), ideas: [first, second] }])
    useIdeaDragAndDrop({
      boardColumns: boardColumns as never,
      ideasStore: { ideas: [first, second], ideasByColumn: { raw: [first, second] }, moveIdea: ideasStore.moveIdea } as never,
      dependencies: mockDnd as never,
    })
    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    const monitor = latestMonitor()
    await monitor.onDrop(dropEvent({}, [{ data: { kind: 'column', columnId: 'raw' } }]))
    await monitor.onDrop(dropEvent({ ideaId: 'missing' }, [{ data: { kind: 'column', columnId: 'raw' } }]))
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, []))
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, [{ data: { kind: 'unsupported', columnId: 'raw' } }]))
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, [{ data: { kind: 'card', columnId: 'raw', ideaId: 'idea-1' } }]))
    expect(ideasStore.moveIdea).not.toHaveBeenCalled()
  })

  it('watches boardColumns and cleans up on change and unmount', async () => {
    const first = makeIdea('idea-1', 'raw', 0)
    const boardColumns = ref([{ ...makeColumn('raw', 0), ideas: [first] }])
    const { unmount } = useIdeaDragAndDrop({
      boardColumns: boardColumns as never,
      ideasStore: { ideas: [first], ideasByColumn: { raw: [first] }, moveIdea: ideasStore.moveIdea } as never,
      dependencies: mockDnd as never,
    }) as { unmount: () => void } & Record<string, unknown>

    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    const initialCleanupCount = cleanups.length
    expect(initialCleanupCount).toBeGreaterThan(0)

    const firstBatch = [...cleanups]
    boardColumns.value = [{ ...makeColumn('raw', 0), ideas: [first, makeIdea('idea-2', 'raw', 1)] }]
    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    expect(firstBatch.every((fn) => fn.mock.calls.length === 1)).toBe(true)

    if (typeof unmount === 'function') {
      const beforeUnmount = cleanups.filter((fn) => fn.mock.calls.length === 0)
      unmount()
      expect(beforeUnmount.every((fn) => fn.mock.calls.length === 1)).toBe(true)
    }
  })

  it('exposes draggedIdeaId via onDragStart', async () => {
    const first = makeIdea('idea-1', 'raw', 0)
    const boardColumns = ref([{ ...makeColumn('raw', 0), ideas: [first] }])
    const { draggedIdeaId } = useIdeaDragAndDrop({
      boardColumns: boardColumns as never,
      ideasStore: { ideas: [first], ideasByColumn: { raw: [first] }, moveIdea: ideasStore.moveIdea } as never,
      dependencies: mockDnd as never,
    })
    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    const monitor = latestMonitor()
    monitor.onDragStart({ source: { data: { ideaId: 'idea-1' } } })
    expect(draggedIdeaId.value).toBe('idea-1')
    await monitor.onDrop(dropEvent({ ideaId: 'idea-1' }, [{ data: { kind: 'column', columnId: 'raw' } }]))
    expect(draggedIdeaId.value).toBeNull()
  })
})
