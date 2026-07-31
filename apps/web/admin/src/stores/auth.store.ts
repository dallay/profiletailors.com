import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface AdminPrincipal {
  principalId: string
  email: string
  displayName: string | null
  platformRoles: string[]
}

export const useAdminAuthStore = defineStore('admin-auth', () => {
  const principal = ref<AdminPrincipal | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => principal.value !== null)
  const hasPlatformAccess = computed(() => (principal.value?.platformRoles.length ?? 0) > 0)

  function hasPermission(permission: string): boolean {
    const roles = principal.value?.platformRoles ?? []
    return computePermissions(roles).has(permission)
  }

  async function hydrateSession(): Promise<void> {
    loading.value = true
    try {
      const response = await fetch('/api/admin/session')
      if (response.ok) {
        principal.value = await response.json()
      } else {
        principal.value = null
      }
    } catch {
      principal.value = null
    } finally {
      loading.value = false
    }
  }

  function clearSession() {
    principal.value = null
  }

  return {
    principal,
    loading,
    isAuthenticated,
    hasPlatformAccess,
    hasPermission,
    hydrateSession,
    clearSession,
  }
})

const ROLE_PERMISSIONS: Record<string, string[]> = {
  PLATFORM_OWNER: [
    'platform.dashboard.read',
    'platform.waitlist.read',
    'platform.waitlist.invite',
    'platform.waitlist.cancel',
    'platform.invitations.read',
    'platform.invitations.resend',
    'platform.invitations.revoke',
    'platform.users.read',
    'platform.users.workspaces.read',
    'platform.audit.read',
    'platform.operators.read',
    'platform.operators.manage',
  ],
  PLATFORM_OPERATOR: [
    'platform.dashboard.read',
    'platform.waitlist.read',
    'platform.waitlist.invite',
    'platform.waitlist.cancel',
    'platform.invitations.read',
    'platform.invitations.resend',
    'platform.invitations.revoke',
    'platform.users.read',
    'platform.users.workspaces.read',
    'platform.audit.read',
    'platform.operators.read',
  ],
  SUPPORT_AGENT: [
    'platform.users.read',
    'platform.waitlist.read',
    'platform.users.workspaces.read',
  ],
  AUDITOR: [
    'platform.audit.read',
    'platform.dashboard.read',
    'platform.waitlist.read',
    'platform.users.read',
    'platform.operators.read',
  ],
}

function computePermissions(roles: string[]): Set<string> {
  const perms = new Set<string>()
  for (const role of roles) {
    for (const perm of ROLE_PERMISSIONS[role] ?? []) {
      perms.add(perm)
    }
  }
  return perms
}
