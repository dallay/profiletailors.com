<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { syncModalDialog } from '@/lib/modal-dialog'

type Props = {
  open: boolean
  invitationId: string
  email: string
  expectedVersion: number | undefined
  pending: boolean
  error: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'confirm', expectedVersion: number): void
}>()

const { t } = useI18n()

const safeVersion = computed(() => props.expectedVersion ?? 0)

function onCancel() {
  if (props.pending) return
  emit('close')
}

function onConfirm() {
  if (props.pending) return
  emit('confirm', safeVersion.value)
}

const dialog = ref<HTMLDialogElement | null>(null)

function onDialogCancel(event: Event) {
  if (props.pending) {
    event.preventDefault()
    return
  }
  emit('close')
}

async function syncDialog(open: boolean) {
  await nextTick()
  syncModalDialog(dialog.value, open)
}

watch(() => props.open, syncDialog)
onMounted(() => syncDialog(props.open))
</script>

<template>
  <Teleport to="body">
    <dialog
      v-if="open"
      ref="dialog"
      class="admin-modal fixed inset-0 m-0 h-full max-h-none w-full max-w-none border-0 bg-transparent p-0 text-inherit z-50 flex items-center justify-center px-4"
      aria-modal="true"
      :aria-label="t('directInvitations.revokeDialog.title')"
      @cancel="onDialogCancel"
      @close="emit('close')"
    >
      <button
        type="button"
        class="admin-modal-overlay absolute inset-0 border-0 p-0 bg-black/70"
        :aria-label="t('common.cancel')"
        @click="onCancel"
      />
      <div class="admin-modal-panel relative z-10 w-full max-w-md rounded-xl border border-border-subtle bg-bg-surface p-6 shadow-2xl">
        <h2 class="mb-2 text-lg font-semibold text-text-display">
          {{ t('directInvitations.revokeDialog.title') }}
        </h2>
        <p class="mb-5 text-sm text-text-secondary">
          {{ t('directInvitations.revokeDialog.message', { email }) }}
        </p>

        <div
          v-if="error"
          role="alert"
          class="mb-4 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error"
        >
          {{ error }}
        </div>

        <div class="flex justify-end gap-3">
          <button
            type="button"
            class="admin-button-secondary text-sm disabled:opacity-40"
            data-testid="revoke-dialog-cancel"
            :disabled="pending"
            @click="onCancel"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="admin-button-danger text-sm disabled:opacity-40"
            data-testid="revoke-dialog-confirm"
            :disabled="pending"
            @click="onConfirm"
          >
            {{ pending ? t('common.loading') : t('directInvitations.revokeDialog.confirm') }}
          </button>
        </div>
      </div>
    </dialog>
  </Teleport>
</template>
