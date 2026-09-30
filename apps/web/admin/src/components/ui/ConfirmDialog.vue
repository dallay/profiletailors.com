<script setup lang="ts">
import { nextTick, onMounted, ref, useId, watch } from 'vue'
import Button from '@/components/ui/AdminButton.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description: string
  confirmText: string
  busy?: boolean
  confirmDisabled?: boolean
  variant?: 'primary' | 'danger'
}>(), {
  busy: false,
  confirmDisabled: false,
  variant: 'primary',
})

const emit = defineEmits<{
  'update:open': [open: boolean]
  confirm: []
}>()

const dialog = ref<HTMLDialogElement | null>(null)
const titleId = `admin-confirm-title-${useId()}`
const descriptionId = `admin-confirm-description-${useId()}`

function preventDismissWhileBusy(event: Event) {
  if (props.busy) event.preventDefault()
}

async function syncDialog(open: boolean) {
  await nextTick()
  if (!dialog.value) return
  if (open && !dialog.value.open) {
    if (typeof dialog.value.showModal === 'function') dialog.value.showModal()
    else dialog.value.setAttribute('open', '')
  }
  if (!open && dialog.value.open) {
    if (typeof dialog.value.close === 'function') dialog.value.close()
    else dialog.value.removeAttribute('open')
  }
}

watch(() => props.open, syncDialog)
onMounted(() => syncDialog(props.open))
</script>

<template>
  <dialog
    ref="dialog"
    role="alertdialog"
    aria-modal="true"
    class="admin-confirm-dialog w-[min(30rem,calc(100vw-2rem))] rounded-xl border border-border-visible bg-bg-surface p-0 text-text-body backdrop:bg-black/70"
    :aria-labelledby="titleId"
    :aria-describedby="descriptionId"
    @cancel="preventDismissWhileBusy"
    @close="emit('update:open', false)"
  >
    <div v-if="open" class="p-5 sm:p-6">
      <h2 :id="titleId" class="text-lg font-medium text-text-display">{{ title }}</h2>
      <p :id="descriptionId" class="mt-3 text-sm leading-6 text-text-secondary">{{ description }}</p>
      <div v-if="$slots.default" class="mt-4">
        <slot />
      </div>
      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <Button variant="secondary" :disabled="busy" @click="emit('update:open', false)">
          {{ $t('common.cancel') }}
        </Button>
        <Button :variant="variant" :disabled="busy || confirmDisabled" @click="emit('confirm')">
          {{ busy ? $t('common.loading') : confirmText }}
        </Button>
      </div>
    </div>
  </dialog>
</template>
