<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePublicCapabilitiesStore } from '@modules/auth/infrastructure/public-capabilities.store'
import { useAcceptInvitationStore } from '@modules/invitation/infrastructure/accept-invitation.store'
import AuthShell from '@modules/auth/presentation/AuthShell.vue'

const props = defineProps<{ token: string }>()

const { t } = useI18n()
const capabilities = usePublicCapabilitiesStore()
const store = useAcceptInvitationStore()

const tokenMissing = computed(() => !props.token || props.token.trim() === '')
const submitted = ref(false)

onMounted(() => {
  void capabilities.load()
})

function canonicalErrorKey(): string {
  const code = store.errorCode
  if (!code) return 'invitation.errors.generic'
  switch (code) {
    case 'INVITATION_NOT_ACCEPTABLE':
      return 'invitation.errors.notAcceptable'
    case 'INVITATION_NOT_FOUND':
      return 'invitation.errors.notFound'
    case 'INVITATION_REQUIRES_LOGIN':
      return 'invitation.errors.requiresLogin'
    case 'INVITATION_RATE_LIMITED':
      return 'invitation.errors.rateLimited'
    case 'MISSING_TOKEN':
      return 'invitation.errors.missingToken'
    default:
      return 'invitation.errors.generic'
  }
}
</script>

<template>
  <AuthShell>
    <div v-if="!capabilities.resolved" role="status" class="text-center text-sm text-text-secondary">
      {{ t('invitation.checkingAvailability') }}
    </div>
    <div v-else-if="!capabilities.invitationAcceptanceEnabled" class="space-y-4 text-center">
      <h1 class="text-2xl font-semibold text-text-display">{{ t('invitation.unavailableTitle') }}</h1>
      <p class="text-sm text-text-secondary">{{ t('invitation.unavailableMessage') }}</p>
    </div>
    <div v-else class="space-y-6">
      <header class="space-y-2 text-center">
        <h1 class="text-2xl font-semibold text-text-display">{{ t('invitation.title') }}</h1>
        <p class="text-sm text-text-secondary">{{ t('invitation.description') }}</p>
      </header>

      <div v-if="tokenMissing" role="alert" class="text-sm text-error">
        {{ t('invitation.errors.missingToken') }}
      </div>
      <div v-else-if="store.hasAccepted" role="status" aria-live="polite" class="space-y-3 text-center">
        <p class="text-sm text-text-secondary">{{ t('invitation.accepted') }}</p>
        <p class="text-sm text-text-secondary">{{ t('invitation.workspaceReady') }}</p>
      </div>
      <div v-else-if="store.errorCode" role="alert" class="text-sm text-error">
        {{ t(canonicalErrorKey()) }}
      </div>
      <form
        v-else
        class="space-y-5"
        :aria-busy="store.pending"
        novalidate
        @submit.prevent="async () => { submitted = true; await store.accept(props.token) }"
      >
        <button
          type="submit"
          :disabled="store.pending || submitted"
          class="min-h-11 w-full rounded-2xl bg-text-display text-bg-primary"
        >
          {{ t(store.pending ? 'invitation.submitting' : 'invitation.submit') }}
        </button>
      </form>
    </div>
  </AuthShell>
</template>