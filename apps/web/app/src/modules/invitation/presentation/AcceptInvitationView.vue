<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { usePublicCapabilitiesStore } from '@modules/auth/infrastructure/public-capabilities.store'
import { useAcceptInvitationStore } from '@modules/invitation/infrastructure/accept-invitation.store'
import { buildLoginRedirect } from './login-redirect'
import AuthShell from '@modules/auth/presentation/AuthShell.vue'

const props = defineProps<{ token: string }>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const capabilities = usePublicCapabilitiesStore()
const store = useAcceptInvitationStore()

const tokenMissing = computed(() => !props.token || props.token.trim() === '')
const submitted = ref(false)
const redirecting = ref(false)

onMounted(() => {
  void capabilities.load()
})

function canonicalErrorKey(): string {
  const code = store.errorCode
  if (!code) return 'invitation.errors.generic'
  switch (code) {
    case 'INVITATION_INVALID':
      return 'invitation.errors.invalid'
    case 'INVITATION_EXPIRED':
      return 'invitation.errors.expired'
    case 'INVITATION_REVOKED':
      return 'invitation.errors.revoked'
    case 'INVITATION_ALREADY_CONSUMED':
      return 'invitation.errors.alreadyConsumed'
    case 'INVITATION_REPLAYED':
      return 'invitation.errors.replayed'
    case 'INVITATION_EMAIL_MISMATCH':
      return 'invitation.errors.emailMismatch'
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

async function handleSubmit(): Promise<void> {
  if (submitted.value || store.pending) return
  submitted.value = true
  const result = await store.accept(props.token, auth.accessToken)
  if (result.errorCode === 'INVITATION_REQUIRES_LOGIN') {
    await router.replace({ name: 'register', query: { invitationToken: props.token } })
    return
  }
  if (result.workspaceId) {
    redirecting.value = true
    try {
      await auth.hydrateSession()
    } catch {
    }
    if (auth.isAuthenticated) {
      await router.replace('/')
    } else {
      const fullPath = buildLoginRedirect(route.path, route.query as Record<string, string>)
      await router.replace({ path: '/login', query: { redirect: fullPath } })
    }
  }
}

watch(redirecting, (value) => {
  if (value) {
    document.title = t('invitation.redirecting')
  }
})
</script>

<template>
  <AuthShell>
    <output v-if="!capabilities.resolved" aria-live="polite" class="block text-center text-sm text-text-secondary">
      {{ t('invitation.checkingAvailability') }}
    </output>
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
      <output v-else-if="store.hasAccepted && redirecting" aria-live="polite" class="block space-y-3 text-center">
        <p class="text-sm text-text-secondary">{{ t('invitation.redirecting') }}</p>
      </output>
      <div v-else-if="store.errorCode" role="alert" class="text-sm text-error">
        {{ t(canonicalErrorKey()) }}
      </div>
      <form
        v-else
        class="space-y-5"
        :aria-busy="store.pending"
        novalidate
        @submit.prevent="handleSubmit"
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
