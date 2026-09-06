<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import DsarRequestForm from './components/DsarRequestForm.vue'
import DsarRequestList from './components/DsarRequestList.vue'
import { AlertTriangle, X } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

// Delete account modal state
const isDeleteModalOpen = ref(false)
const deleteConfirmationText = ref('')
const isDeleting = ref(false)
const deleteError = ref<string | null>(null)

function openDeleteModal() {
  deleteConfirmationText.value = ''
  deleteError.value = null
  isDeleteModalOpen.value = true
}

function closeDeleteModal() {
  isDeleteModalOpen.value = false
}

async function handleDeleteAccount() {
  if (deleteConfirmationText.value.trim() !== 'DELETE') return

  isDeleting.value = true
  deleteError.value = null

  try {
    const res = await auth.apiFetch('/api/v1/account/close', {
      method: 'POST',
      body: JSON.stringify({
        confirmation: 'DELETE',
      }),
    })

    if (!res.ok) {
      const problem = await res.json().catch(() => null)
      deleteError.value = problem?.detail || t('settings.accountClosure.error')
      isDeleting.value = false
      return
    }

    // On success: clear session and redirect to login
    await auth.logout()
    router.push('/login')
  } catch (err) {
    deleteError.value = t('settings.accountClosure.error')
    isDeleting.value = false
  }
}
</script>

<template>
  <div class="space-y-8">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-text-display">
        {{ t('settings.headers.privacyTitle') }}
      </h1>
      <p class="text-sm text-text-secondary mt-1">
        {{ t('settings.headers.privacySubtitle') }}
      </p>
    </div>

    <!-- YOUR DATA (DSAR Form) -->
    <div class="rounded-xl border border-border-subtle bg-bg-surface p-6 space-y-6 shadow-sm">
      <DsarRequestForm />
    </div>

    <!-- MY REQUESTS (DSAR Request List) -->
    <div class="rounded-xl border border-border-subtle bg-bg-surface p-6 space-y-6 shadow-sm">
      <DsarRequestList />
    </div>

    <!-- DANGER ZONE (Account Deletion) -->
    <div class="rounded-xl border border-red-500/30 bg-red-500/5 p-6 space-y-6 shadow-sm">
      <h2 class="text-xs font-semibold text-red-400 uppercase tracking-wider">
        {{ t('settings.dangerZone.title') }}
      </h2>

      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div class="space-y-1">
          <h3 class="text-base font-semibold text-text-display">
            {{ t('settings.dangerZone.deleteAccountTitle') }}
          </h3>
          <p class="text-xs text-text-secondary max-w-xl">
            {{ t('settings.dangerZone.deleteAccountDesc') }}
          </p>
        </div>

        <button
          type="button"
          class="px-4 py-2 rounded-lg bg-red-600 text-white text-xs font-medium hover:bg-red-500 transition-colors shrink-0"
          @click="openDeleteModal"
        >
          {{ t('settings.dangerZone.deleteAccountBtn') }}
        </button>
      </div>
    </div>

    <!-- Delete Account Modal -->
    <div
      v-if="isDeleteModalOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-account-modal-title"
    >
      <div
        class="w-full max-w-md rounded-2xl border border-border-subtle bg-bg-surface p-6 shadow-2xl space-y-6 relative"
      >
        <div class="flex items-center justify-between border-b border-border-subtle pb-4">
          <div class="flex items-center gap-2.5 text-red-400">
            <AlertTriangle class="w-5 h-5 shrink-0" />
            <h3 id="delete-account-modal-title" class="text-lg font-bold text-text-display">
              {{ t('settings.dangerZone.modalTitle') }}
            </h3>
          </div>
          <button
            type="button"
            class="text-text-muted hover:text-text-display p-1 rounded-lg hover:bg-bg-subtle transition-colors"
            @click="closeDeleteModal"
          >
            <X class="w-5 h-5" />
            <span class="sr-only">Close</span>
          </button>
        </div>

        <p class="text-xs text-text-secondary">
          {{ t('settings.dangerZone.modalDesc') }}
        </p>

        <!-- Error feedback -->
        <div
          v-if="deleteError"
          class="p-3 rounded-lg border border-red-500/30 bg-red-500/10 text-red-400 text-xs font-medium"
        >
          {{ deleteError }}
        </div>

        <form class="space-y-4" @submit.prevent="handleDeleteAccount">
          <div class="space-y-1.5">
            <label for="delete-confirm-input" class="block text-xs font-medium text-text-secondary">
              {{ t('settings.dangerZone.confirmInstruction') }}
            </label>
            <input
              id="delete-confirm-input"
              v-model="deleteConfirmationText"
              type="text"
              required
              :placeholder="t('settings.dangerZone.confirmPlaceholder')"
              class="w-full rounded-lg border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-display focus:border-red-500 focus:outline-none"
            />
          </div>

          <div class="flex items-center justify-end gap-3 pt-4 border-t border-border-subtle">
            <button
              type="button"
              class="px-4 py-2 rounded-lg border border-border-subtle text-xs font-medium text-text-secondary hover:text-text-display hover:bg-bg-subtle transition-colors"
              @click="closeDeleteModal"
            >
              {{ t('settings.dangerZone.cancel') }}
            </button>
            <button
              type="submit"
              :disabled="deleteConfirmationText.trim() !== 'DELETE' || isDeleting"
              class="px-4 py-2 rounded-lg bg-red-600 text-white text-xs font-medium hover:bg-red-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              {{ isDeleting ? t('settings.dangerZone.deleting') : t('settings.dangerZone.deleteAccountBtn') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
