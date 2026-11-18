import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  acceptInvitationRequest,
  type AcceptInvitationResult,
} from '@modules/invitation/infrastructure/invitation-api'

export const useAcceptInvitationStore = defineStore('invitation-accept', () => {
  const pending = ref(false)
  const workspaceId = ref<string | null>(null)
  const membershipStatus = ref<string | null>(null)
  const errorCode = ref<string | null>(null)
  const errorStatus = ref<number | null>(null)

  const hasAccepted = computed(() => workspaceId.value !== null)

  function reset() {
    pending.value = false
    workspaceId.value = null
    membershipStatus.value = null
    errorCode.value = null
    errorStatus.value = null
  }

  /**
   * Submit a raw invitation token to the platform-admin invitation acceptance endpoint.
   *
   * Returns the normalised outcome (success fields or error fields). The store NEVER
   * echoes the raw token outside the immediate API invocation; downstream renderers and
   * logs see the resolved workspace/membership/error fields only.
   */
  async function accept(token: string): Promise<AcceptInvitationResult> {
    if (!token || token.trim() === '') {
      errorCode.value = 'MISSING_TOKEN'
      errorStatus.value = 0
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'MISSING_TOKEN',
        errorStatus: 0,
      }
    }

    pending.value = true
    errorCode.value = null
    errorStatus.value = null
    workspaceId.value = null
    membershipStatus.value = null

    try {
      const result = await acceptInvitationRequest(token)
      workspaceId.value = result.workspaceId
      membershipStatus.value = result.membershipStatus
      errorCode.value = result.errorCode
      errorStatus.value = result.errorStatus
      return result
    } catch (cause) {
      const apiError = cause as { status?: number; code?: string; errorCode?: string }
      errorStatus.value = apiError.status ?? 500
      errorCode.value = apiError.code ?? apiError.errorCode ?? 'INTERNAL_ERROR'
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: errorCode.value,
        errorStatus: errorStatus.value,
      }
    } finally {
      pending.value = false
    }
  }

  return {
    accept,
    errorCode,
    errorStatus,
    hasAccepted,
    membershipStatus,
    pending,
    reset,
    workspaceId,
  }
})
