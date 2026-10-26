import { beforeEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import {
  normalizeColumnOrder,
  normalizeIdeas,
  reorderWithinList,
  useIdeasStore,
} from './ideas.store'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

function makeColumn(id: string, order: number): IdeaColumn {
  return { id, name: id, order }
}

function makeIdea(id: string, columnId: string, orderInColumn: number): Idea {
  return {
    id,
    workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
    title: id,
    notes: null,
    tags: [],
    links: [],
    columnId,
    orderInColumn,
    convertedToPublicationId: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

describe('ideas store helpers', () => {
  it('reorderWithinList moves an item to target index', () => {
    const list = ['a', 'b', 'c', 'd']
    expect(reorderWithinList(list, 0, 2)).toEqual(['b', 'c', 'a', 'd'])
  })

  it('normalizeColumnOrder sorts by order and reindexes', () => {
    const columns = [makeColumn('done', 2), makeColumn('raw', 0), makeColumn('in-progress', 1)]
    expect(normalizeColumnOrder(columns)).toEqual([
      makeColumn('raw', 0),
      makeColumn('in-progress', 1),
      makeColumn('done', 2),
    ])
  })

  it('normalizeIdeas reindexes each column from 0', () => {
    const ideas = [makeIdea('c', 'done', 9), makeIdea('a', 'raw', 4), makeIdea('b', 'raw', 8)]

    const normalized = normalizeIdeas(ideas)
    const rawIdeas = normalized
      .filter((idea) => idea.columnId === 'raw')
      .sort((left, right) => left.orderInColumn - right.orderInColumn)

    const doneIdeas = normalized.filter((idea) => idea.columnId === 'done')

    expect(rawIdeas.map((idea) => idea.id)).toEqual(['a', 'b'])
    expect(rawIdeas.map((idea) => idea.orderInColumn)).toEqual([0, 1])
    expect(doneIdeas[0]?.orderInColumn).toBe(0)
  })
})

describe('ideas store actions', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  function prepareStore() {
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
    return { auth, store: useIdeasStore() }
  }

  it('loads and normalizes board payloads returned in column envelopes', async () => {
    const { auth, store } = prepareStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockImplementation(async (path) => {
      if (path === '/api/ideas/columns') {
        return {
          columns: [
            { id: 'raw', name: 'Raw', order: 2 },
            { id: 'done', name: 'Done', order: 0 },
          ],
        }
      }
      return {
        columns: [
          {
            id: 'raw',
            ideas: [
              {
                ...makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 9),
                orderInColumn: undefined,
              },
            ],
          },
        ],
      }
    })

    await store.loadBoard()

    expect(apiFetch).toHaveBeenCalledTimes(2)
    expect(store.orderedColumns.map((column) => column.id)).toEqual(['done', 'raw'])
    expect(store.ideas[0]?.orderInColumn).toBe(0)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('loads direct array and ideas-envelope responses and falls back to default columns', async () => {
    const { auth, store } = prepareStore()
    vi.spyOn(auth, 'apiFetch')
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce({
        ideas: [makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 2)],
      })

    await store.loadBoard()

    expect(store.orderedColumns.map((column) => column.id)).toEqual(['raw', 'in-progress', 'done'])
    expect(store.ideas[0]?.id).toBe('0ca69867-76ef-5e75-a8c3-3933fb8faec4')
    expect(store.loading).toBe(false)
  })

  it('records a board load error and clears loading after a transport failure', async () => {
    const { auth, store } = prepareStore()
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('board unavailable'))

    await store.loadBoard()

    expect(store.error).toBe('board unavailable')
    expect(store.loading).toBe(false)
  })

  it('guards board loading when authentication or workspace context is missing', async () => {
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    const store = useIdeasStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch')

    await store.loadBoard()
    workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
    await store.loadBoard()

    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('creates an idea with trimmed input and the first column fallback', async () => {
    const { auth, store } = prepareStore()
    const created = {
      ...makeIdea('29e38a90-83cc-5bd6-b35d-fe36041817ea', 'raw', 4),
      workspaceId: '',
    }
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(created)

    const result = await store.createIdea({ title: '  New idea  ', tags: ['launch'] })

    expect(apiFetch).toHaveBeenCalledWith(
      '/api/ideas',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          title: 'New idea',
          notes: null,
          tags: ['launch'],
          links: [],
          columnId: 'raw',
          orderInColumn: 0,
        }),
      }),
    )
    expect(result.workspaceId).toBe('a219727d-019f-544c-95f9-ac30f00cc8b2')
    expect(store.saving).toBe(false)
  })

  it('creates an idea with API defaults and clears saving after an API error', async () => {
    const { auth, store } = prepareStore()
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('create failed'))

    await expect(store.createIdea({ title: 'New idea' })).rejects.toThrow('create failed')

    expect(store.saving).toBe(false)
    expect(store.error).toBeNull()
  })

  it('requires workspace context for every mutating action', async () => {
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    const store = useIdeasStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch')

    await expect(store.createIdea({ title: 'Idea' })).rejects.toThrow(
      'Workspace context is required',
    )
    await expect(
      store.updateIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', { title: 'Updated' }),
    ).rejects.toThrow('Workspace context is required')
    await expect(
      store.moveIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', {
        columnId: 'done',
        orderInColumn: 0,
      }),
    ).rejects.toThrow('Workspace context is required')
    await expect(store.deleteIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')).rejects.toThrow(
      'Workspace context is required',
    )
    await expect(store.convertIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')).rejects.toThrow(
      'Workspace context is required',
    )
    await expect(store.updateColumns([makeColumn('raw', 0)])).rejects.toThrow(
      'Workspace context is required',
    )
    expect(workspace.activeWorkspaceId).toBeNull()
    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('rolls back an optimistic move when the API rejects it', async () => {
    const { auth, store } = prepareStore()
    const first = makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0)
    const second = makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1', 'done', 0)
    store.ideas.push(first, second)
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('move failed'))

    await store.moveIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', {
      columnId: 'done',
      orderInColumn: 0,
    })

    expect(
      store.ideas.find((idea) => idea.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')?.columnId,
    ).toBe('raw')
    expect(store.error).toBe('move failed')
  })

  it('reorders within a column and no-ops for unknown ideas', async () => {
    const { auth, store } = prepareStore()
    store.ideas.push(
      makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0),
      makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1', 'raw', 1),
    )
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(undefined)

    await store.moveIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', {
      columnId: 'raw',
      orderInColumn: 1,
    })
    const raw = store.ideasByColumn.raw ?? []
    expect(raw.map((idea) => idea.id)).toEqual([
      'a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1',
      '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
    ])
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/ideas/0ca69867-76ef-5e75-a8c3-3933fb8faec4/move',
      expect.objectContaining({ method: 'PATCH' }),
    )

    await store.moveIdea('missing', { columnId: 'done', orderInColumn: 0 })
    expect(apiFetch).toHaveBeenCalledTimes(1)
  })

  it('updates, deletes, converts, and clears local state', async () => {
    const { auth, store } = prepareStore()
    const idea = makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0)
    store.ideas.push(idea)
    const apiFetch = vi
      .spyOn(auth, 'apiFetch')
      .mockResolvedValueOnce({ ...idea, title: 'Updated' })
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce({ publicationId: '90894321-b522-516a-beed-741683bf63ca' })

    await store.updateIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', { title: 'Updated' })
    await store.deleteIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')
    store.ideas.push(idea)
    const publicationId = await store.convertIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')

    expect(publicationId).toBe('90894321-b522-516a-beed-741683bf63ca')
    expect(apiFetch).toHaveBeenCalledTimes(3)
    expect(
      store.ideas.find((item) => item.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')
        ?.convertedToPublicationId,
    ).toBe('90894321-b522-516a-beed-741683bf63ca')
    store.clearState()
    expect(store.ideas).toEqual([])
    expect(store.error).toBeNull()
  })

  it('restores saving flags after update, delete, and convert errors', async () => {
    const { auth, store } = prepareStore()
    store.ideas.push(makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0))
    const apiFetch = vi
      .spyOn(auth, 'apiFetch')
      .mockRejectedValueOnce(new Error('update failed'))
      .mockRejectedValueOnce(new Error('delete failed'))
      .mockRejectedValueOnce(new Error('convert failed'))

    await expect(
      store.updateIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', { title: 'Updated' }),
    ).rejects.toThrow('update failed')
    expect(store.saving).toBe(false)
    await expect(store.deleteIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')).rejects.toThrow(
      'delete failed',
    )
    expect(store.saving).toBe(false)
    await expect(store.convertIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')).rejects.toThrow(
      'convert failed',
    )
    expect(store.saving).toBe(false)
    expect(apiFetch).toHaveBeenCalledTimes(3)
  })

  it('validates columns before requesting and applies fallback to invalid ideas', async () => {
    const { auth, store } = prepareStore()
    store.columns = [makeColumn('raw', 0), makeColumn('done', 1)]
    store.ideas.push(
      makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'removed', 3),
      makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1', 'raw', 1),
    )
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(undefined)

    await expect(store.updateColumns([])).rejects.toThrow('At least one column is required')
    expect(apiFetch).not.toHaveBeenCalled()

    await store.updateColumns([makeColumn('done', 2), makeColumn('raw', 0)])
    expect(store.columns.map((column) => column.id)).toEqual(['raw', 'done'])
    expect(
      store.ideas.find((idea) => idea.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')?.columnId,
    ).toBe('raw')
    expect(store.saving).toBe(false)
  })

  it('creates local columns with normalized name, color, and next order', () => {
    const { store } = prepareStore()

    const withColor = store.createLocalColumn('  Ideas  ', '#fff')
    const withoutColor = store.createLocalColumn('More')

    expect(withColor).toMatchObject({ name: 'Ideas', color: '#fff', order: 3 })
    expect(withColor.id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    )
    expect(withoutColor).toMatchObject({ name: 'More', color: null, order: 3 })
  })

  it('clears ideas, columns, flags, and errors', () => {
    const { store } = prepareStore()
    store.ideas.push(makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0))
    store.columns = [makeColumn('custom', 4)]
    store.error = 'failed'
    store.loading = true
    store.saving = true

    store.clearState()

    expect(store.ideas).toEqual([])
    expect(store.columns.map((column) => column.id)).toEqual(['raw', 'in-progress', 'done'])
    expect(store.error).toBeNull()
    expect(store.loading).toBe(false)
    expect(store.saving).toBe(false)
  })

  it('associates publication via PATCH and keeps same column', async () => {
    const { auth, store } = prepareStore()
    store.ideas.push(makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0))
    const updated = {
      ...makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0),
      convertedToPublicationId: '5907d0e4-0390-51bb-8711-85387d7562f0',
    }
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(updated)
    const result = await store.associatePublication(
      '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
      '5907d0e4-0390-51bb-8711-85387d7562f0',
    )
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/ideas/0ca69867-76ef-5e75-a8c3-3933fb8faec4',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ convertedToPublicationId: '5907d0e4-0390-51bb-8711-85387d7562f0' }),
      }),
    )
    expect(result.convertedToPublicationId).toBe('5907d0e4-0390-51bb-8711-85387d7562f0')
    expect(store.ideas.find((i) => i.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')?.columnId).toBe(
      'raw',
    )
    expect(
      store.ideas.find((i) => i.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')
        ?.convertedToPublicationId,
    ).toBe('5907d0e4-0390-51bb-8711-85387d7562f0')
  })

  it('associate does not auto-move or delete on success and handles null', async () => {
    const { auth, store } = prepareStore()
    store.ideas.push(
      makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0),
      makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1', 'raw', 1),
    )
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
      ...makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0),
      convertedToPublicationId: null,
    })
    await store.associatePublication('0ca69867-76ef-5e75-a8c3-3933fb8faec4', null)
    expect(store.ideas.map((i) => i.id)).toContain('0ca69867-76ef-5e75-a8c3-3933fb8faec4')
    expect(store.ideas.find((i) => i.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')?.columnId).toBe(
      'raw',
    )
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/ideas/0ca69867-76ef-5e75-a8c3-3933fb8faec4',
      expect.objectContaining({ body: JSON.stringify({ convertedToPublicationId: null }) }),
    )
  })

  it('associate restores saving flag after error and does not mark converted on failure', async () => {
    const { auth, store } = prepareStore()
    store.ideas.push(makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0))
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('associate failed'))
    await expect(
      store.associatePublication(
        '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
        '5907d0e4-0390-51bb-8711-85387d7562f0',
      ),
    ).rejects.toThrow('associate failed')
    expect(store.saving).toBe(false)
    expect(
      store.ideas.find((i) => i.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')
        ?.convertedToPublicationId,
    ).toBeNull()
  })
})
