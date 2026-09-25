import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWorkspaceStore } from './workspace.store'
import * as authApi from '@modules/auth/infrastructure/auth-api'

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  fetchWorkspaces: vi.fn(),
}))

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('initializes with values from localStorage if available', () => {
    localStorage.setItem('pt_active_workspace_id', 'ws-123')
    localStorage.setItem('pt_active_workspace_name', 'Acme Corp')

    const store = useWorkspaceStore()

    expect(store.activeWorkspaceId).toBe('ws-123')
    expect(store.workspaceName).toBe('Acme Corp')
  })

  it('initializes as null when localStorage is empty', () => {
    const store = useWorkspaceStore()

    expect(store.activeWorkspaceId).toBeNull()
    expect(store.workspaceName).toBeNull()
    expect(store.activeWorkspace).toBeNull()
  })

  it('setActiveWorkspaceId updates state and localStorage', () => {
    const store = useWorkspaceStore()

    store.setActiveWorkspaceId('ws-999')

    expect(store.activeWorkspaceId).toBe('ws-999')
    expect(localStorage.getItem('pt_active_workspace_id')).toBe('ws-999')

    store.setActiveWorkspaceId(null)

    expect(store.activeWorkspaceId).toBeNull()
    expect(localStorage.getItem('pt_active_workspace_id')).toBeNull()
  })

  it('setWorkspaceName updates state and localStorage', () => {
    const store = useWorkspaceStore()

    store.setWorkspaceName('My Workspace')

    expect(store.workspaceName).toBe('My Workspace')
    expect(localStorage.getItem('pt_active_workspace_name')).toBe('My Workspace')

    store.setWorkspaceName(null)

    expect(store.workspaceName).toBeNull()
    expect(localStorage.getItem('pt_active_workspace_name')).toBeNull()
  })

  it('updateWorkspaceIcon updates the icon of matching workspace', () => {
    const store = useWorkspaceStore()
    store.workspaces = [
      { workspaceId: 'ws-1', name: 'WS 1', role: 'ADMIN', createdAt: '2025-01-01', icon: 'rocket' },
      { workspaceId: 'ws-2', name: 'WS 2', role: 'MEMBER', createdAt: '2025-01-01', icon: null },
    ]

    store.updateWorkspaceIcon('ws-2', 'star')

    expect(store.workspaces[1]?.icon).toBe('star')
  })

  it('loadWorkspaces fetches workspaces and auto-selects first if none selected', async () => {
    const mockList = [
      { workspaceId: 'ws-1', name: 'First WS', role: 'ADMIN', createdAt: '2025-01-01', icon: null },
      {
        workspaceId: 'ws-2',
        name: 'Second WS',
        role: 'MEMBER',
        createdAt: '2025-01-01',
        icon: null,
      },
    ]
    vi.mocked(authApi.fetchWorkspaces).mockResolvedValueOnce(mockList)

    const store = useWorkspaceStore()
    await store.loadWorkspaces('token-abc')

    expect(authApi.fetchWorkspaces).toHaveBeenCalledWith('token-abc')
    expect(store.workspaces).toEqual(mockList)
    expect(store.activeWorkspaceId).toBe('ws-1')
    expect(store.workspaceName).toBe('First WS')
    expect(store.activeWorkspace).toEqual(mockList[0])
    expect(store.isLoadingWorkspaces).toBe(false)
  })

  it('loadWorkspaces clears active selection when workspace list is empty', async () => {
    localStorage.setItem('pt_active_workspace_id', 'ws-stale')
    localStorage.setItem('pt_active_workspace_name', 'Stale WS')

    vi.mocked(authApi.fetchWorkspaces).mockResolvedValueOnce([])

    const store = useWorkspaceStore()
    await store.loadWorkspaces('token-abc')

    expect(store.workspaces).toEqual([])
    expect(store.activeWorkspaceId).toBeNull()
    expect(store.workspaceName).toBeNull()
  })

  it('loadWorkspaces preserves current selection if present in loaded list', async () => {
    localStorage.setItem('pt_active_workspace_id', 'ws-2')
    localStorage.setItem('pt_active_workspace_name', 'Old Name')

    const mockList = [
      { workspaceId: 'ws-1', name: 'First WS', role: 'ADMIN', createdAt: '2025-01-01', icon: null },
      {
        workspaceId: 'ws-2',
        name: 'Updated Second WS',
        role: 'MEMBER',
        createdAt: '2025-01-01',
        icon: null,
      },
    ]
    vi.mocked(authApi.fetchWorkspaces).mockResolvedValueOnce(mockList)

    const store = useWorkspaceStore()
    await store.loadWorkspaces('token-abc')

    expect(store.activeWorkspaceId).toBe('ws-2')
    expect(store.workspaceName).toBe('Updated Second WS')
  })

  it('loadWorkspaces handles API errors gracefully', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.mocked(authApi.fetchWorkspaces).mockRejectedValueOnce(new Error('Network error'))

    const store = useWorkspaceStore()
    await store.loadWorkspaces('token-abc')

    expect(store.isLoadingWorkspaces).toBe(false)
    expect(consoleSpy).toHaveBeenCalledWith('Failed to load workspaces:', expect.any(Error))
    consoleSpy.mockRestore()
  })

  it('$reset resets state and clears localStorage keys', () => {
    localStorage.setItem('pt_active_workspace_id', 'ws-1')
    localStorage.setItem('pt_active_workspace_name', 'WS 1')

    const store = useWorkspaceStore()
    store.workspaces = [
      { workspaceId: 'ws-1', name: 'WS 1', role: 'ADMIN', createdAt: '2025-01-01', icon: null },
    ]

    store.$reset()

    expect(store.activeWorkspaceId).toBeNull()
    expect(store.workspaceName).toBeNull()
    expect(store.workspaces).toEqual([])
    expect(localStorage.getItem('pt_active_workspace_id')).toBeNull()
    expect(localStorage.getItem('pt_active_workspace_name')).toBeNull()
  })
})
