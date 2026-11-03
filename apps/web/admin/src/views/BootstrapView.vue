<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiRequestError, apiFetch, ensureApiSuccess } from '@/lib/api'

const { t } = useI18n()
const invitationToken = consumeInvitationToken()
const currentTermsVersion = ref<string | null>(null)
const password = ref('')
const acceptedTerms = ref(false)
const loading = ref(true)
const pending = ref(false)
const completed = ref(false)
const error = ref<string | null>(null)
const errorAlert = ref<HTMLElement | null>(null)

onMounted(loadRequirements)

async function loadRequirements(): Promise<void> {
  try {
    const response = await ensureApiSuccess(await apiFetch('/api/bootstrap/requirements'))
    const payload: unknown = await response.json()
    if (!isBootstrapRequirements(payload)) throw new Error('Invalid bootstrap requirements response')
    currentTermsVersion.value = payload.currentTermsVersion
  } catch {
    error.value = 'requirementsUnavailable'
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (
    pending.value ||
    loading.value ||
    !invitationToken ||
    !currentTermsVersion.value ||
    !acceptedTerms.value ||
    password.value.length < 12
  ) {
    return
  }

  pending.value = true
  error.value = null
  try {
    await ensureApiSuccess(
      await apiFetch('/api/bootstrap/complete', {
        method: 'POST',
        body: JSON.stringify({
          invitationToken,
          password: password.value,
          acceptedTermsVersion: currentTermsVersion.value,
        }),
      }),
    )
    completed.value = true
  } catch (requestError) {
    error.value = requestError instanceof ApiRequestError && requestError.code === 'BOOTSTRAP_UNAVAILABLE'
      ? 'unavailable'
      : 'genericError'
    errorAlert.value?.focus()
  } finally {
    pending.value = false
  }
}

function consumeInvitationToken(): string | null {
  const token = window.location.hash.slice(1)
  if (window.location.hash) {
    window.history.replaceState(
      window.history.state,
      '',
      `${window.location.pathname}${window.location.search}`,
    )
  }
  return token || null
}

function isBootstrapRequirements(value: unknown): value is { currentTermsVersion: string } {
  return typeof value === 'object' && value !== null &&
    'currentTermsVersion' in value && typeof value.currentTermsVersion === 'string'
}
</script>

<template>
  <main class="auth-shell dot-grid flex min-h-screen items-center justify-center bg-bg-primary px-4 py-10 text-text-body">
    <section class="w-full max-w-lg rounded-[28px] border border-border-subtle bg-bg-surface p-6 sm:p-8">
      <header class="mb-8 space-y-3 text-center">
        <p class="label-mono text-text-secondary">Profile Tailors</p>
        <h1 class="text-2xl font-semibold text-text-display">{{ t('bootstrap.title') }}</h1>
        <p class="text-sm text-text-secondary">{{ t('bootstrap.description') }}</p>
      </header>

      <output v-if="loading" data-testid="bootstrap-loading" class="block text-sm text-text-secondary">
        {{ t('bootstrap.loading') }}
      </output>

      <output v-else-if="completed" data-testid="bootstrap-complete" class="block rounded-2xl border border-border-visible px-4 py-3 text-sm text-text-body">
        {{ t('bootstrap.emailVerification') }}
      </output>

      <template v-else>
        <p v-if="error === 'requirementsUnavailable'" role="alert" data-testid="bootstrap-requirements-error" class="mb-5 text-sm text-error">
          {{ t(`bootstrap.${error}`) }}
        </p>

        <form v-else class="space-y-5" name="platform-bootstrap" data-testid="bootstrap-form" :aria-busy="pending" @submit.prevent="submit">
          <p v-if="currentTermsVersion" class="rounded-2xl border border-border-visible px-4 py-3 text-sm text-text-secondary" data-testid="bootstrap-terms-version">
            {{ t('bootstrap.termsVersion', { version: currentTermsVersion }) }}
          </p>

          <div class="space-y-2">
            <label for="bootstrap-password" class="text-sm font-medium text-text-display">{{ t('bootstrap.password') }}</label>
            <input
              id="bootstrap-password"
              v-model="password"
              type="password"
              name="password"
              autocomplete="new-password"
              minlength="12"
              required
              data-testid="bootstrap-password"
              class="min-h-11 w-full rounded-2xl border border-border-visible bg-bg-primary px-4 text-text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display"
            >
            <p class="text-xs text-text-secondary">{{ t('bootstrap.passwordHelp') }}</p>
          </div>

          <label class="flex min-h-11 items-start gap-3 text-sm text-text-body">
            <input
              v-model="acceptedTerms"
              type="checkbox"
              name="acceptedTerms"
              required
              data-testid="bootstrap-terms-accepted"
              class="mt-1 size-4 accent-text-display focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display"
            >
            <span>{{ t('bootstrap.termsAcceptance') }}</span>
          </label>

          <p v-if="error" ref="errorAlert" role="alert" tabindex="-1" data-testid="bootstrap-error" class="rounded-2xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error">
            {{ t(`bootstrap.${error}`) }}
          </p>

          <button
            type="submit"
            :disabled="pending || loading || !acceptedTerms || password.length < 12"
            class="min-h-11 w-full rounded-2xl bg-text-display px-4 font-semibold text-bg-primary disabled:opacity-60"
          >
            {{ t(pending ? 'bootstrap.submitting' : 'bootstrap.submit') }}
          </button>
        </form>
      </template>
    </section>
  </main>
</template>
