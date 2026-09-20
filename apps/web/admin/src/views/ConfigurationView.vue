<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

type RegistrationMode = 'OPEN' | 'INVITE_ONLY' | 'CLOSED'

const REGISTRATION_MODES: readonly RegistrationMode[] = ['OPEN', 'INVITE_ONLY', 'CLOSED']

interface RegistrationModeResult {
  mode: RegistrationMode
}

const { t } = useI18n()
const authStore = useAdminAuthStore()

const currentMode = ref<RegistrationMode | null>(null)
const selectedMode = ref<RegistrationMode>('CLOSED')
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const canManage = computed(() => authStore.hasPermission('platform.configuration.manage'))

function isRegistrationMode(value: unknown): value is RegistrationMode {
  return typeof value === 'string' && (REGISTRATION_MODES as readonly string[]).includes(value)
}

async function fetchCurrentMode(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const response = await authStore.request('/api/admin/configuration/registration-mode')
    if (!response.ok) {
      error.value = t('common.error')
      return
    }
    const body = (await response.json()) as RegistrationModeResult
    if (isRegistrationMode(body.mode)) {
      currentMode.value = body.mode
      selectedMode.value = body.mode
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function changeMode(): Promise<void> {
  successMessage.value = null
  if (!window.confirm(t('configuration.changeConfirm', { mode: selectedMode.value }))) return

  saving.value = true
  error.value = null
  try {
    const response = await authStore.request('/api/admin/configuration/registration-mode', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': crypto.randomUUID(),
      },
      body: JSON.stringify({ mode: selectedMode.value }),
    })
    if (!response.ok) {
      error.value = t('common.error')
      return
    }
    const body = (await response.json()) as RegistrationModeResult
    if (isRegistrationMode(body.mode)) {
      currentMode.value = body.mode
      selectedMode.value = body.mode
    }
    successMessage.value = t('configuration.changeSuccess')
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

onMounted(fetchCurrentMode)
</script>

<template>
  <div class="admin-page p-5 sm:p-8">
    <h1 class="mb-6 text-2xl font-semibold text-text-display">{{ t('configuration.title') }}</h1>

    <div v-if="loading" class="text-text-secondary">{{ t('common.loading') }}</div>
    <div v-else-if="error" role="alert" class="mb-4 text-error">{{ error }}</div>
    <template v-else>
      <div class="admin-card mb-6 p-4">
        <p class="label-mono mb-1 text-text-secondary">{{ t('configuration.currentMode') }}</p>
        <p class="text-sm text-text-body" data-testid="current-mode">{{ currentMode }}</p>
      </div>

      <div v-if="canManage" class="mb-4 flex flex-wrap items-end gap-3">
        <div>
          <label for="registration-mode-select" class="mb-1 block text-sm text-text-body">
            {{ t('configuration.changeTo') }}
          </label>
          <select id="registration-mode-select" v-model="selectedMode" class="admin-input text-sm">
            <option v-for="mode in REGISTRATION_MODES" :key="mode" :value="mode">{{ mode }}</option>
          </select>
        </div>
        <button type="button" class="admin-button-secondary" :disabled="saving" @click="changeMode">
          {{ saving ? t('common.loading') : t('configuration.changeTo') }}
        </button>
      </div>

      <p v-if="successMessage" role="status" class="text-sm text-success">{{ successMessage }}</p>
    </template>
  </div>
</template>
