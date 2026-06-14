<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const isRegisterMode = computed(() => route.name === 'register')
const alternateRoute = computed(() => isRegisterMode.value ? '/login' : '/register')

const email = ref('')
const password = ref('')
const formError = ref<string | null>(null)

if (auth.error) {
  formError.value = auth.error
}

// Clear form state when navigating between /login and /register
// (both use the same AuthView component, so setup() runs only once)
watch(() => route.name, () => {
  formError.value = null
  email.value = ''
  password.value = ''
})

async function handleSubmit() {
  formError.value = null
  auth.clearError()

  try {
    if (isRegisterMode.value) {
      await auth.registerWithPassword({
        email: email.value,
        password: password.value,
      })
    } else {
      await auth.loginWithPassword({
        email: email.value,
        password: password.value,
      })
    }

    const redirectTo = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirectTo)
  } catch {
    formError.value = auth.error
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg-primary text-text-body dot-grid">
    <div class="mx-auto flex min-h-screen max-w-6xl items-center px-6 py-10 lg:px-10">
      <div class="grid w-full gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
        <section class="space-y-8">
          <div class="space-y-4">
            <div class="inline-flex items-center gap-2 rounded-full border border-border-visible bg-bg-surface px-4 py-2 font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
              <span class="size-2 rounded-full bg-text-display" />
              {{ $t('auth.badge') }}
            </div>
            <div class="space-y-3">
              <h1 class="max-w-xl text-4xl font-light tracking-tight text-text-display sm:text-5xl">
                {{ $t('auth.heroTitle') }}
              </h1>
              <p class="max-w-2xl text-sm leading-7 text-text-secondary sm:text-base">
                {{ $t('auth.heroDescription') }}
              </p>
            </div>
          </div>

          <div class="grid gap-4 sm:grid-cols-3">
            <div class="rounded-2xl border border-border-subtle bg-bg-surface p-5">
              <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
                {{ $t('auth.security') }}
              </p>
              <p class="mt-3 text-sm text-text-body">
                {{ $t('auth.securityDesc') }}
              </p>
            </div>
            <div class="rounded-2xl border border-border-subtle bg-bg-surface p-5">
              <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
                {{ $t('auth.focus') }}
              </p>
              <p class="mt-3 text-sm text-text-body">
                {{ $t('auth.focusDesc') }}
              </p>
            </div>
            <div class="rounded-2xl border border-border-subtle bg-bg-surface p-5">
              <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
                {{ $t('auth.workflow') }}
              </p>
              <p class="mt-3 text-sm text-text-body">
                {{ $t('auth.workflowDesc') }}
              </p>
            </div>
          </div>
        </section>

        <section class="rounded-[28px] border border-border-subtle bg-bg-surface p-7 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] sm:p-8">
          <div class="space-y-2">
            <p class="font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
              {{ $t('auth.localAccess') }}
            </p>
            <h2 class="text-2xl font-light text-text-display">
              {{ $t(isRegisterMode ? 'auth.titleRegister' : 'auth.titleLogin') }}
            </h2>
            <p class="text-sm leading-6 text-text-secondary">
              {{ $t(isRegisterMode ? 'auth.subtitleRegister' : 'auth.subtitleLogin') }}
            </p>
          </div>

          <form class="mt-8 space-y-5" @submit.prevent="handleSubmit">
            <div class="space-y-2">
              <label class="font-mono text-[10px] font-bold uppercase tracking-[0.16em] text-text-secondary" for="email">
                {{ $t('auth.email') }}
              </label>
              <input
                id="email"
                v-model="email"
                type="email"
                autocomplete="email"
                :placeholder="$t('auth.emailPlaceholder', { at: '@' })"
                class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                required
              >
            </div>

            <div class="space-y-2">
              <label class="font-mono text-[10px] font-bold uppercase tracking-[0.16em] text-text-secondary" for="password">
                {{ $t('auth.password') }}
              </label>
              <input
                id="password"
                v-model="password"
                type="password"
                autocomplete="current-password"
                :placeholder="$t('auth.passwordPlaceholder')"
                class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                required
              >
            </div>

            <div
              v-if="formError"
              class="rounded-2xl border border-error/30 bg-error/10 px-4 py-3 text-sm text-error"
            >
              {{ formError }}
            </div>

            <Button type="submit" class="w-full justify-center" :disabled="auth.isLoading">
              {{ auth.isLoading ? '...' : $t(isRegisterMode ? 'auth.submitRegister' : 'auth.submitLogin') }}
            </Button>
          </form>

          <div class="mt-6 flex items-center justify-between gap-4 border-t border-border-subtle pt-5 text-sm">
            <span class="text-text-secondary">{{ $t(isRegisterMode ? 'auth.alternateLabelRegister' : 'auth.alternateLabelLogin') }}</span>
            <RouterLink
              :to="alternateRoute"
              class="font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-text-display transition-opacity hover:opacity-70"
            >
              {{ $t(isRegisterMode ? 'auth.alternateActionRegister' : 'auth.alternateActionLogin') }}
            </RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
