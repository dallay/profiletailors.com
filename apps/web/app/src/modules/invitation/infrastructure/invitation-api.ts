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
 * Submits an invitation token to the invitation acceptance endpoint.
 *
 * @param token - The raw invitation token to submit.
 * @returns The invitation acceptance result, including workspace and membership data on success or error details on failure.
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
      body: JSON.stringify({ token }),
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
