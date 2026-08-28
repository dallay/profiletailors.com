import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiFetch, ensureApiSuccess, readApiJson } from '@/lib/api'

const ROLE_PERMISSIONS = {
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
} as const satisfies Record<string, readonly string[]>

export type PlatformRole = keyof typeof ROLE_PERMISSIONS
export type PlatformPermission = (typeof ROLE_PERMISSIONS)[PlatformRole][number]

export type AdminPrincipal = {
  principalId: string
  email: string
  displayName: string | null
  platformRoles: PlatformRole[]
}

export type AuthTokens = {
  accessToken: string
  tokenType: string
  expiresIn: number
  principalId: string
  email: string
  username: string | null
  emailStatus: string
  workspaceId: string | null
}

export const useAdminAuthStore = defineStore('admin-auth', () => {
  const principal = ref<AdminPrincipal | null>(null)
  const accessToken = ref<string | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => principal.value !== null)
  const hasPlatformAccess = computed(() => (principal.value?.platformRoles.length ?? 0) > 0)

  function hasPermission(permission: PlatformPermission): boolean {
    const roles = principal.value?.platformRoles ?? []
    return computePermissions(roles).has(permission)
  }

  async function hydrateSession(): Promise<void> {
    loading.value = true
    try {
      const tokens = await refreshSession()
      if (!tokens) return
      await fetchAdminSession()
    } catch {
      clearSession()
    } finally {
      loading.value = false
    }
  }

  async function signIn(email: string, password: string): Promise<void> {
    loading.value = true
    try {
      const response = await apiFetch('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      await ensureApiSuccess(response)
      const tokens = await readApiJson<AuthTokens>(response)
      accessToken.value = tokens.accessToken
      await fetchAdminSession()
    } catch (error) {
      clearSession()
      throw error
    } finally {
      loading.value = false
    }
  }

  async function refreshSession(): Promise<AuthTokens | null> {
    const response = await apiFetch('/api/auth/refresh', { method: 'POST' })
    if (response.status === 401) {
      clearSession()
      return null
    }
    await ensureApiSuccess(response)
    const tokens = await readApiJson<AuthTokens>(response)
    accessToken.value = tokens.accessToken
    return tokens
  }

  async function request(path: string, init: RequestInit = {}): Promise<Response> {
    let response = await apiFetch(path, init, accessToken.value)
    if (response.status === 401 && path !== '/api/auth/refresh') {
      const tokens = await refreshSession()
      if (tokens) response = await apiFetch(path, init, accessToken.value)
    }
    return response
  }

  async function signOut(): Promise<void> {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' }, accessToken.value)
    } catch {
      clearSession()
      return
    }
    clearSession()
  }

  function clearSession(): void {
    principal.value = null
    accessToken.value = null
  }

  async function fetchAdminSession(): Promise<void> {
    const response = await request('/api/admin/session')
    await ensureApiSuccess(response)
    const candidate = await readApiJson<AdminPrincipal>(response)
    principal.value = {
      ...candidate,
      platformRoles: candidate.platformRoles.filter(isPlatformRole),
    }
  }

  return {
    principal,
    accessToken,
    loading,
    isAuthenticated,
    hasPlatformAccess,
    hasPermission,
    hydrateSession,
    signIn,
    refreshSession,
    request,
    signOut,
    clearSession,
  }
})

function computePermissions(roles: PlatformRole[]): Set<PlatformPermission> {
  const perms = new Set<PlatformPermission>()
  for (const role of roles) {
    for (const perm of ROLE_PERMISSIONS[role] ?? []) {
      perms.add(perm)
    }
  }
  return perms
}

function isPlatformRole(role: string): role is PlatformRole {
  return role in ROLE_PERMISSIONS
}
