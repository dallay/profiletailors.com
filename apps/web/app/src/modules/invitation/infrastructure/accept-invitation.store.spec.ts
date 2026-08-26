import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAcceptInvitationStore } from './accept-invitation.store'
import type { AcceptInvitationResult } from './invitation-api'

const mockAcceptInvitationRequest = vi.fn()

vi.mock('./invitation-api', async (importOriginal) => {
  const actual = (await importOriginal()) as typeof import('./invitation-api')
  return {
    ...actual,
    acceptInvitationRequest: (...args: unknown[]) => mockAcceptInvitationRequest(...args),
  }
})

function successResult(overrides: Partial<AcceptInvitationResult> = {}): AcceptInvitationResult {
  return {
    workspaceId: 'ws-abc',
    membershipStatus: 'ACTIVE',
    errorCode: null,
    errorStatus: null,
    ...overrides,
  }
}

function errorResult(code: string, status: number): AcceptInvitationResult {
  return {
    workspaceId: null,
    membershipStatus: null,
    errorCode: code,
    errorStatus: status,
  }
}

describe('useAcceptInvitationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockAcceptInvitationRequest.mockReset()
  })

  it('exposes initial state', () => {
    const store = useAcceptInvitationStore()

    expect(store.pending).toBe(false)
    expect(store.workspaceId).toBeNull()
    expect(store.membershipStatus).toBeNull()
    expect(store.errorCode).toBeNull()
    expect(store.errorStatus).toBeNull()
    expect(store.hasAccepted).toBe(false)
  })

  it('returns MISSING_TOKEN and sets error state for empty token without calling api', async () => {
    const store = useAcceptInvitationStore()

    const result = await store.accept('')

    expect(result).toEqual(errorResult('MISSING_TOKEN', 0))
    expect(store.errorCode).toBe('MISSING_TOKEN')
    expect(store.errorStatus).toBe(0)
    expect(store.workspaceId).toBeNull()
    expect(store.hasAccepted).toBe(false)
    expect(mockAcceptInvitationRequest).not.toHaveBeenCalled()
    expect(store.pending).toBe(false)
  })

  it('returns MISSING_TOKEN for whitespace token', async () => {
    const store = useAcceptInvitationStore()

    const result = await store.accept('   ')

    expect(result.errorCode).toBe('MISSING_TOKEN')
    expect(result.errorStatus).toBe(0)
    expect(mockAcceptInvitationRequest).not.toHaveBeenCalled()
  })

  it('transitions pending true during accept and false after success', async () => {
    let resolveRequest!: (value: AcceptInvitationResult) => void
    mockAcceptInvitationRequest.mockReturnValue(
      new Promise<AcceptInvitationResult>((resolve) => {
        resolveRequest = resolve
      }),
    )

    const store = useAcceptInvitationStore()
    const promise = store.accept('valid-token')

    expect(store.pending).toBe(true)
    expect(store.errorCode).toBeNull()
    expect(store.errorStatus).toBeNull()

    resolveRequest(successResult())
    const result = await promise

    expect(result.workspaceId).toBe('ws-abc')
    expect(store.pending).toBe(false)
    expect(store.workspaceId).toBe('ws-abc')
    expect(store.membershipStatus).toBe('ACTIVE')
    expect(store.hasAccepted).toBe(true)
  })

  it('stores success fields and clears previous error', async () => {
    const store = useAcceptInvitationStore()
    store.errorCode = 'PREVIOUS'
    store.errorStatus = 400
    mockAcceptInvitationRequest.mockResolvedValue(
      successResult({ workspaceId: 'ws-new', membershipStatus: 'PENDING' }),
    )

    const result = await store.accept('tok')

    expect(result.workspaceId).toBe('ws-new')
    expect(store.workspaceId).toBe('ws-new')
    expect(store.membershipStatus).toBe('PENDING')
    expect(store.errorCode).toBeNull()
    expect(store.errorStatus).toBeNull()
    expect(store.hasAccepted).toBe(true)
  })

  it('handles success payload with null workspace as not accepted', async () => {
    mockAcceptInvitationRequest.mockResolvedValue(
      successResult({ workspaceId: null, membershipStatus: null }),
    )
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.workspaceId).toBeNull()
    expect(store.hasAccepted).toBe(false)
  })

  it('stores api-returned error fields when request resolves with error', async () => {
    mockAcceptInvitationRequest.mockResolvedValue(errorResult('INVITATION_NOT_FOUND', 404))
    const store = useAcceptInvitationStore()
    store.workspaceId = 'ws-old'
    store.membershipStatus = 'ACTIVE'

    const result = await store.accept('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_FOUND')
    expect(result.errorStatus).toBe(404)
    expect(store.errorCode).toBe('INVITATION_NOT_FOUND')
    expect(store.errorStatus).toBe(404)
    expect(store.workspaceId).toBeNull()
    expect(store.membershipStatus).toBeNull()
    expect(store.hasAccepted).toBe(false)
    expect(store.pending).toBe(false)
  })

  it('exposes hasAccepted false when api returns error', async () => {
    mockAcceptInvitationRequest.mockResolvedValue(errorResult('INVITATION_RATE_LIMITED', 429))
    const store = useAcceptInvitationStore()

    await store.accept('tok')

    expect(store.hasAccepted).toBe(false)
  })

  it('clears previous workspace on error result', async () => {
    const store = useAcceptInvitationStore()
    mockAcceptInvitationRequest.mockResolvedValueOnce(successResult({ workspaceId: 'ws-1' }))
    await store.accept('tok')
    expect(store.workspaceId).toBe('ws-1')

    mockAcceptInvitationRequest.mockResolvedValueOnce(errorResult('INVITATION_NOT_ACCEPTABLE', 400))
    await store.accept('tok2')

    expect(store.workspaceId).toBeNull()
    expect(store.membershipStatus).toBeNull()
  })

  it('maps thrown error with status and code', async () => {
    mockAcceptInvitationRequest.mockRejectedValue({
      status: 401,
      code: 'INVITATION_REQUIRES_LOGIN',
    })
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result).toEqual(errorResult('INVITATION_REQUIRES_LOGIN', 401))
    expect(store.errorCode).toBe('INVITATION_REQUIRES_LOGIN')
    expect(store.errorStatus).toBe(401)
    expect(store.pending).toBe(false)
    expect(store.workspaceId).toBeNull()
  })

  it('maps thrown error with status and errorCode', async () => {
    mockAcceptInvitationRequest.mockRejectedValue({
      status: 404,
      errorCode: 'INVITATION_NOT_FOUND',
    })
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_FOUND')
    expect(result.errorStatus).toBe(404)
  })

  it('prefers code over errorCode when both present', async () => {
    mockAcceptInvitationRequest.mockRejectedValue({
      status: 400,
      code: 'CODE_A',
      errorCode: 'CODE_B',
    })
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.errorCode).toBe('CODE_A')
  })

  it('defaults to INTERNAL_ERROR and 500 when thrown error has no fields', async () => {
    mockAcceptInvitationRequest.mockRejectedValue({})
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
    expect(result.errorStatus).toBe(500)
  })

  it('defaults status to 500 when only code is present', async () => {
    mockAcceptInvitationRequest.mockRejectedValue({ code: 'SOME_CODE' })
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.errorStatus).toBe(500)
    expect(result.errorCode).toBe('SOME_CODE')
  })

  it('handles thrown Error instance as INTERNAL_ERROR', async () => {
    mockAcceptInvitationRequest.mockRejectedValue(new Error('network down'))
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
    expect(result.errorStatus).toBe(500)
  })

  it('handles thrown string as INTERNAL_ERROR', async () => {
    mockAcceptInvitationRequest.mockRejectedValue('oops')
    const store = useAcceptInvitationStore()

    const result = await store.accept('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
    expect(result.errorStatus).toBe(500)
  })

  it('resets pending to false after thrown error', async () => {
    let rejectRequest!: (reason: unknown) => void
    mockAcceptInvitationRequest.mockReturnValue(
      new Promise<AcceptInvitationResult>((_, reject) => {
        rejectRequest = reject
      }),
    )

    const store = useAcceptInvitationStore()
    const promise = store.accept('tok')
    expect(store.pending).toBe(true)

    rejectRequest({ status: 500, code: 'INTERNAL_ERROR' })
    await promise

    expect(store.pending).toBe(false)
  })

  it('reset clears all state', async () => {
    mockAcceptInvitationRequest.mockResolvedValue(successResult())
    const store = useAcceptInvitationStore()
    await store.accept('tok')
    expect(store.hasAccepted).toBe(true)

    store.reset()

    expect(store.pending).toBe(false)
    expect(store.workspaceId).toBeNull()
    expect(store.membershipStatus).toBeNull()
    expect(store.errorCode).toBeNull()
    expect(store.errorStatus).toBeNull()
    expect(store.hasAccepted).toBe(false)
  })

  it('reset after error clears error fields', async () => {
    mockAcceptInvitationRequest.mockResolvedValue(errorResult('INVITATION_NOT_ACCEPTABLE', 400))
    const store = useAcceptInvitationStore()
    await store.accept('tok')
    expect(store.errorCode).toBe('INVITATION_NOT_ACCEPTABLE')

    store.reset()

    expect(store.errorCode).toBeNull()
    expect(store.errorStatus).toBeNull()
  })

  it('allows successive accepts after reset', async () => {
    const store = useAcceptInvitationStore()
    mockAcceptInvitationRequest.mockResolvedValueOnce(successResult({ workspaceId: 'ws-1' }))
    await store.accept('tok1')
    store.reset()

    mockAcceptInvitationRequest.mockResolvedValueOnce(successResult({ workspaceId: 'ws-2' }))
    const result = await store.accept('tok2')

    expect(result.workspaceId).toBe('ws-2')
    expect(store.workspaceId).toBe('ws-2')
  })

  it('hasAccepted reacts to workspaceId changes', async () => {
    const store = useAcceptInvitationStore()
    expect(store.hasAccepted).toBe(false)

    mockAcceptInvitationRequest.mockResolvedValue(successResult({ workspaceId: 'ws-x' }))
    await store.accept('tok')
    expect(store.hasAccepted).toBe(true)

    store.workspaceId = null
    expect(store.hasAccepted).toBe(false)
  })
})
