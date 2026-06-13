import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'pt_active_workspace_id'

function readStoredWorkspaceId(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(STORAGE_KEY)
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const selectedWorkspaceId = ref<string | null>(readStoredWorkspaceId())

  const activeWorkspaceId = computed(() => selectedWorkspaceId.value)

  function setActiveWorkspaceId(workspaceId: string | null) {
    selectedWorkspaceId.value = workspaceId
    if (typeof localStorage === 'undefined') return

    if (workspaceId) {
      localStorage.setItem(STORAGE_KEY, workspaceId)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  return {
    activeWorkspaceId,
    setActiveWorkspaceId,
  }
})
