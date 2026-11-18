import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiFetch, ensureApiSuccess, readApiJson, resolveApiUrl } from '@/lib/api'

const ROLE_PERMISSIONS = {
  PLATFORM_OWNER: [
    'platform.dashboard.read',
    'platform.system.build-info.read',
    'platform.waitlist.read',
    'platform.waitlist.invite',
    'platform.waitlist.cancel',
    'platform.invitations.read',
    'platform.invitations.create',
    'platform.invitations.resend',
    'platform.invitations.revoke',
    'platform.users.read',
    'platform.users.workspaces.read',
    'platform.users.manage',
    'platform.audit.read',
    'platform.operators.read',
    'platform.operators.manage',
    'platform.configuration.read',
    'platform.configuration.manage',
    'platform.notifications.read',
    'platform.notifications.manage',
    'platform.governance.read',
    'platform.governance.manage',
  ],
  PLATFORM_OPERATOR: [
    'platform.dashboard.read',
    'platform.waitlist.read',
    'platform.waitlist.invite',
    'platform.waitlist.cancel',
    'platform.invitations.read',
    'platform.invitations.create',
    'platform.invitations.resend',
    'platform.invitations.revoke',
    'platform.users.read',
    'platform.users.workspaces.read',
    'platform.users.manage',
    'platform.audit.read',
    'platform.operators.read',
    'platform.configuration.read',
    'platform.notifications.read',
    'platform.notifications.manage',
    'platform.governance.read',
    'platform.governance.manage',
  ],
  SUPPORT_AGENT: [
    'platform.users.read',
    'platform.waitlist.read',
    'platform.users.workspaces.read',
    'platform.notifications.read',
  ],
  AUDITOR: [
    'platform.audit.read',
    'platform.dashboard.read',
    'platform.waitlist.read',
    'platform.users.read',
    'platform.operators.read',
    'platform.configuration.read',
    'platform.notifications.read',
    'platform.governance.read',
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

export const useAdminAuthStore = defineStore('admin-auth', () => {
  const principal = ref<AdminPrincipal | null>(null)
  const accessDenied = ref(false)
  const loading = ref(false)
  let csrfToken: string | null = null

  const isAuthenticated = computed(() => principal.value !== null)
  const hasPlatformAccess = computed(() => (principal.value?.platformRoles.length ?? 0) > 0)

  function hasPermission(permission: PlatformPermission): boolean {
    const roles = principal.value?.platformRoles ?? []
    return computePermissions(roles).has(permission)
  }

  async function hydrateSession(): Promise<void> {
    loading.value = true
    try {
      await fetchAdminSession()
    } catch {
      clearSession()
    } finally {
      loading.value = false
    }
  }

  function signIn(returnTo?: string): void {
    accessDenied.value = false
    const destination = safeReturnPath(returnTo)
    window.location.assign(
      `${resolveApiUrl('/oauth2/authorization/pt-admin')}?returnTo=${encodeURIComponent(destination)}`,
    )
  }

  async function request(path: string, init: RequestInit = {}): Promise<Response> {
    const method = (init.method ?? 'GET').toUpperCase()
    const headers = new Headers(init.headers)
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
      csrfToken ??= await fetchCsrfToken()
      headers.set('X-CSRF-TOKEN', csrfToken)
    }
    const response = await apiFetch(path, { ...init, headers })
    if (response.status === 401) clearSession()
    else if (response.status === 403) denyAdminAccess()
    return response
  }

  async function signOut(): Promise<void> {
    try {
      const response = await request('/api/auth/logout', { method: 'POST' })
      await ensureApiSuccess(response)
    } catch {
      clearSession()
      return
    }
    clearSession()
  }

  function clearSession(): void {
    principal.value = null
    csrfToken = null
  }

  function denyAdminAccess(): void {
    accessDenied.value = true
    clearSession()
  }

  async function fetchAdminSession(): Promise<void> {
    const response = await apiFetch('/api/admin/session')
    await ensureApiSuccess(response)
    const candidate = await readApiJson<AdminPrincipal>(response)
    principal.value = {
      ...candidate,
      platformRoles: candidate.platformRoles.filter(isPlatformRole),
    }
  }

  return {
    principal,
    accessDenied,
    loading,
    isAuthenticated,
    hasPlatformAccess,
    hasPermission,
    hydrateSession,
    signIn,
    request,
    signOut,
    clearSession,
  }
})

async function fetchCsrfToken(): Promise<string> {
  const response = await apiFetch('/api/auth/csrf')
  await ensureApiSuccess(response)
  const payload = await readApiJson<{ token: string }>(response)
  return payload.token
}

function safeReturnPath(value: string | undefined): string {
  if (!value?.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/'
  try {
    const url = new URL(value, window.location.origin)
    if (url.origin !== window.location.origin) return '/'
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return '/'
  }
}

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
