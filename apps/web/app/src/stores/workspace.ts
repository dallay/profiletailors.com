import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { type WorkspaceSummary, fetchWorkspaces } from '@/lib/auth-api'

const STORAGE_KEY = 'pt_active_workspace_id'
const NAME_STORAGE_KEY = 'pt_active_workspace_name'

function readStoredWorkspaceId(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(STORAGE_KEY)
}

function readStoredWorkspaceName(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(NAME_STORAGE_KEY)
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const selectedWorkspaceId = ref<string | null>(readStoredWorkspaceId())
  const workspaceName = ref<string | null>(readStoredWorkspaceName())
  const workspaces = ref<WorkspaceSummary[]>([])
  const isLoadingWorkspaces = ref(false)

  const activeWorkspaceId = computed(() => selectedWorkspaceId.value)

  const activeWorkspace = computed(
    () => workspaces.value.find((ws) => ws.workspaceId === selectedWorkspaceId.value) ?? null,
  )

  function setActiveWorkspaceId(workspaceId: string | null) {
    selectedWorkspaceId.value = workspaceId
    if (typeof localStorage === 'undefined') return

    if (workspaceId) {
      localStorage.setItem(STORAGE_KEY, workspaceId)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }

    // Sync the workspace name from the list
    const match = workspaces.value.find((ws) => ws.workspaceId === workspaceId)
    setWorkspaceName(match?.name ?? null)
  }

  function setWorkspaceName(name: string | null) {
    workspaceName.value = name
    if (typeof localStorage === 'undefined') return

    if (name) {
      localStorage.setItem(NAME_STORAGE_KEY, name)
    } else {
      localStorage.removeItem(NAME_STORAGE_KEY)
    }
  }

  function updateWorkspaceIcon(workspaceId: string, icon: string | null) {
    const match = workspaces.value.find((ws) => ws.workspaceId === workspaceId)
    if (match) {
      match.icon = icon
    }
  }

  /**
   * Fetch all workspaces for the authenticated user.
   * Auto-selects the first workspace if none is currently selected.
   */
  async function loadWorkspaces(accessToken: string) {
    isLoadingWorkspaces.value = true
    try {
      const list = await fetchWorkspaces(accessToken)
      workspaces.value = list

      // Auto-select if nothing selected yet, or if the selected one is no longer in the list
      if (
        !selectedWorkspaceId.value ||
        !list.some((ws) => ws.workspaceId === selectedWorkspaceId.value)
      ) {
        if (list.length > 0) {
          const first = list[0]!
          setActiveWorkspaceId(first.workspaceId)
        } else {
          // No workspaces available — clear stale selection
          setActiveWorkspaceId(null)
          setWorkspaceName(null)
        }
      } else {
        // Sync name from list for the current selection
        const match = list.find((ws) => ws.workspaceId === selectedWorkspaceId.value)
        setWorkspaceName(match?.name ?? null)
      }
    } catch (error) {
      console.error('Failed to load workspaces:', error)
    } finally {
      isLoadingWorkspaces.value = false
    }
  }

  function $reset() {
    selectedWorkspaceId.value = null
    workspaceName.value = null
    workspaces.value = []
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem(NAME_STORAGE_KEY)
    }
  }

  return {
    activeWorkspace,
    activeWorkspaceId,
    isLoadingWorkspaces,
    loadWorkspaces,
    setActiveWorkspaceId,
    setWorkspaceName,
    updateWorkspaceIcon,
    workspaces,
    workspaceName,
    $reset,
  }
})
