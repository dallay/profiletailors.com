import type { ApiError } from '@modules/auth/infrastructure/auth-api'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'

export type AcceptInvitationApiResponse = {
  workspaceId?: string
  membershipStatus?: string
}

export type AcceptInvitationResult = {
  workspaceId: string | null
  membershipStatus: string | null
  errorCode: string | null
  errorStatus: number | null
}

/**
 * Submit a raw invitation token to the platform-admin invitation acceptance endpoint.
 *
 * The call is intentionally un-authenticated: the backend identifies the invitation by
 * the token alone. On success the backend establishes a refresh-token cookie so the
 * subsequent SPA session can use the regular `hydrateSession` path. We send
 * `credentials: 'include'` so that cookie reaches the server.
 *
 * If the server already has an authenticated session for the invitee, the same cookie
 * carries the identity and the backend binds the invitation to that identity.
 */
export async function acceptInvitationRequest(token: string): Promise<AcceptInvitationResult> {
  let response: Response
  try {
    response = await fetch(resolveApiUrl('/api/invitations/accept'), {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/vnd.api.v1+json',
      },
      body: JSON.stringify({ invitationToken: token }),
    })
  } catch {
    return {
      workspaceId: null,
      membershipStatus: null,
      errorCode: 'INTERNAL_ERROR',
      errorStatus: 500,
    }
  }

  if (!response.ok) {
    let payload: ApiError | null = null
    try {
      payload = (await response.json()) as ApiError
    } catch {
      payload = null
    }
    const errorCode = payload?.errorCode ?? payload?.code ?? classifyStatus(response.status)
    return {
      workspaceId: null,
      membershipStatus: null,
      errorCode,
      errorStatus: response.status,
    }
  }

  let payload: AcceptInvitationApiResponse = {}
  try {
    payload = (await response.json()) as AcceptInvitationApiResponse
  } catch {
    payload = {}
  }

  return {
    workspaceId: payload.workspaceId ?? null,
    membershipStatus: payload.membershipStatus ?? null,
    errorCode: null,
    errorStatus: null,
  }
}

function classifyStatus(status: number): string {
  if (status === 400 || status === 409) return 'INVITATION_NOT_ACCEPTABLE'
  if (status === 401) return 'INVITATION_REQUIRES_LOGIN'
  if (status === 404) return 'INVITATION_NOT_FOUND'
  if (status === 429) return 'INVITATION_RATE_LIMITED'
  return 'INTERNAL_ERROR'
}
