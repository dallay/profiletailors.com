import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import { useIdeasStore } from './ideas.store'
import type { Idea } from '@modules/ideas/domain'

vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

import { toast } from 'vue-sonner'

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

describe('ideas store rollback with toast', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('rolls back optimistic move and shows toast on failure', async () => {
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
    const store = useIdeasStore()
    const first = makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'raw', 0)
    const second = makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1', 'done', 0)
    store.ideas.push(first, second)
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('move failed'))
    await store.moveIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', {
      columnId: 'done',
      orderInColumn: 0,
    })
    expect(store.ideas.find((i) => i.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')?.columnId).toBe(
      'raw',
    )
    expect(toast.error).toHaveBeenCalled()
    expect(store.error).toBe('move failed')
  })

  it('remaps orphans to fallback minBy order column', async () => {
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
    const store = useIdeasStore()
    store.columns = [
      { id: 'raw', name: 'Raw', order: 2 },
      { id: 'done', name: 'Done', order: 0 },
    ]
    store.ideas.push(makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4', 'removed', 0))
    vi.spyOn(auth, 'apiFetch').mockResolvedValue(undefined)
    await store.updateColumns([
      { id: 'done', name: 'Done', order: 0 },
      { id: 'raw', name: 'Raw', order: 1 },
    ])
    expect(store.ideas.find((i) => i.id === '0ca69867-76ef-5e75-a8c3-3933fb8faec4')?.columnId).toBe(
      'done',
    )
  })
})
