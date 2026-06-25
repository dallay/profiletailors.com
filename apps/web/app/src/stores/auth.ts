import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  type ApiError,
  type AuthTokens,
  type CurrentUserProfile,
  createApiFetch,
  getCurrentUserProfile,
  login,
  logoutSession,
  refreshSession,
  register,
} from '@/lib/auth-api'
import { useWorkspaceStore } from './workspace'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface AuthUser {
  principalId: string
  email: string | null
  username: string | null
  emailStatus: string | null
  displayIdentity: string
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mapTokensToUser(tokens: AuthTokens): AuthUser {
  return {
    principalId: tokens.principalId,
    email: tokens.email,
    username: tokens.username,
    emailStatus: tokens.emailStatus,
    displayIdentity: tokens.username || tokens.email || tokens.principalId,
  }
}

function mapProfileToUser(profile: CurrentUserProfile, currentUser: AuthUser | null): AuthUser {
  return {
    principalId: profile.principalId,
    email: profile.email,
    username: profile.username,
    emailStatus: currentUser?.emailStatus ?? null,
    displayIdentity: profile.displayIdentity,
  }
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useAuthStore = defineStore('auth', () => {
  const workspace = useWorkspaceStore()

  // Access token lives ONLY in memory — never persisted to localStorage
  const _accessToken = ref<string | null>(null)
  const user = ref<AuthUser | null>(null)
  const isLoading = ref(false)
  const isRefreshingProfile = ref(false)
  const error = ref<string | null>(null)
  const sessionChecked = ref(false)

  // ---------------------------------------------------------------------------
  // Computed
  // ---------------------------------------------------------------------------

  const isAuthenticated = computed(() => Boolean(_accessToken.value))
  const accessToken = computed(() => _accessToken.value)

  const defaultDisplayName = 'PT'
  const displayName = computed(
    () =>
      user.value?.displayIdentity ||
      user.value?.username ||
      user.value?.email ||
      defaultDisplayName,
  )

  const userInitials = computed(() => {
    const source = displayName.value.trim()

    if (!source) return defaultDisplayName

    const parts = source.split(/\s+/).filter(Boolean)
    if (parts.length === 1) return (parts[0] ?? defaultDisplayName).slice(0, 2).toUpperCase()

    return parts
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase()
  })

  // ---------------------------------------------------------------------------
  // apiFetch — wired to this store's token + refresh logic
  // ---------------------------------------------------------------------------

  /**
   * Use this instead of raw fetch for any authenticated API call.
   * Handles Bearer injection + silent 401 retry with token rotation.
   */
  const authenticatedApiFetch = createApiFetch({
    getToken: () => _accessToken.value,
    getWorkspaceId: () => workspace.activeWorkspaceId,
    onRefresh: async () => {
      const tokens = await refreshSession()
      if (tokens) {
        _applyTokens(tokens)
        return tokens.accessToken
      }
      return null
    },
    onUnauthenticated: () => _clearSession(),
  })
  const apiFetch = authenticatedApiFetch
  const apiFetchRaw = authenticatedApiFetch.raw

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  function _applyTokens(tokens: AuthTokens) {
    _accessToken.value = tokens.accessToken
    user.value = mapTokensToUser(tokens)

    // If the backend returned a workspace ID, always apply it
    // This handles both first-time registration and account switching
    if (tokens.workspaceId) {
      workspace.setActiveWorkspaceId(tokens.workspaceId)
    }
  }

  function _clearSession() {
    _accessToken.value = null
    user.value = null
    error.value = null
    sessionChecked.value = true
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  async function loginWithPassword(payload: { email: string; password: string }) {
    isLoading.value = true
    error.value = null

    try {
      const tokens = await login(payload)
      _applyTokens(tokens)
      await _loadProfile()
      return tokens
    } catch (error_) {
      const apiError = error_ as ApiError
      error.value = apiError.detail ?? 'Unable to sign in.'
      throw error_
    } finally {
      isLoading.value = false
      sessionChecked.value = true
    }
  }

  async function registerWithPassword(payload: { email: string; password: string }) {
    isLoading.value = true
    error.value = null

    try {
      const tokens = await register(payload)
      _applyTokens(tokens)
      await _loadProfile()
      return tokens
    } catch (error_) {
      const apiError = error_ as ApiError
      error.value = apiError.detail ?? 'Unable to create your account.'
      throw error_
    } finally {
      isLoading.value = false
      sessionChecked.value = true
    }
  }

  let _hydratePromise: Promise<void> | null = null

  /**
   * Bootstraps the session from the server using the HttpOnly refresh-token cookie.
   * Call this once on app mount to restore an existing session without localStorage.
   * Idempotent: concurrent calls share the same in-flight promise.
   */
  async function hydrateSession() {
    if (_hydratePromise) return _hydratePromise

    _hydratePromise = (async () => {
      try {
        const tokens = await refreshSession()

        if (!tokens) {
          // No active session — that's fine
          sessionChecked.value = true
          return
        }

        _applyTokens(tokens)
        await _loadProfile()
      } catch {
        // refreshSession already swallows 401; anything else is a transient error
        _clearSession()
      } finally {
        sessionChecked.value = true
        _hydratePromise = null
      }
    })()

    return _hydratePromise
  }

  async function refreshProfile() {
    if (!_accessToken.value) {
      _clearSession()
      return null
    }

    isRefreshingProfile.value = true

    try {
      const profile = await getCurrentUserProfile(_accessToken.value)
      user.value = mapProfileToUser(profile, user.value)
      error.value = null
      sessionChecked.value = true
      return profile
    } catch (error_) {
      const apiError = error_ as ApiError
      if (apiError.status === 401) {
        _clearSession()
      } else {
        error.value = apiError.detail ?? 'Unable to refresh your session.'
      }
      throw error_
    } finally {
      isRefreshingProfile.value = false
    }
  }

  async function logout() {
    // Tell the server to invalidate the refresh-token session (clears cookie too)
    await logoutSession()
    _clearSession()
  }

  function clearError() {
    error.value = null
  }

  // ---------------------------------------------------------------------------
  // Private
  // ---------------------------------------------------------------------------

  async function _loadProfile() {
    if (!_accessToken.value) return

    isRefreshingProfile.value = true

    try {
      const profile = await getCurrentUserProfile(_accessToken.value)
      user.value = mapProfileToUser(profile, user.value)
      error.value = null
    } catch {
      // Non-critical — user data from tokens is good enough for now
    } finally {
      isRefreshingProfile.value = false
    }
  }

  // ---------------------------------------------------------------------------
  // Public surface
  // ---------------------------------------------------------------------------

  return {
    accessToken,
    apiFetch,
    apiFetchRaw,
    clearError,
    displayName,
    error,
    hydrateSession,
    isAuthenticated,
    isLoading,
    isRefreshingProfile,
    loginWithPassword,
    logout,
    refreshProfile,
    registerWithPassword,
    sessionChecked,
    user,
    userInitials,
  }
})
