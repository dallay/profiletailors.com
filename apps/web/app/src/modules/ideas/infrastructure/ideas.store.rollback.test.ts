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
    workspaceId: 'ws-1',
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
    workspace.setActiveWorkspaceId('workspace-1')
    const store = useIdeasStore()
    const first = makeIdea('idea-1', 'raw', 0)
    const second = makeIdea('idea-2', 'done', 0)
    store.ideas.push(first, second)
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('move failed'))
    await store.moveIdea('idea-1', { columnId: 'done', orderInColumn: 0 })
    expect(store.ideas.find((i) => i.id === 'idea-1')?.columnId).toBe('raw')
    expect(toast.error).toHaveBeenCalled()
    expect(store.error).toBe('move failed')
  })

  it('remaps orphans to fallback minBy order column', async () => {
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    workspace.setActiveWorkspaceId('workspace-1')
    const store = useIdeasStore()
    store.columns = [
      { id: 'raw', name: 'Raw', order: 2 },
      { id: 'done', name: 'Done', order: 0 },
    ]
    store.ideas.push(makeIdea('idea-1', 'removed', 0))
    vi.spyOn(auth, 'apiFetch').mockResolvedValue(undefined)
    await store.updateColumns([
      { id: 'done', name: 'Done', order: 0 },
      { id: 'raw', name: 'Raw', order: 1 },
    ])
    expect(store.ideas.find((i) => i.id === 'idea-1')?.columnId).toBe('done')
  })
})
