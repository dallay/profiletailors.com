<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DsarRequestType, CorrectionData } from '@modules/settings/infrastructure/privacy.store'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const { t } = useI18n()

const emit = defineEmits<{
  submit: [payload: { type: DsarRequestType; notes?: string; correctionData?: CorrectionData }]
}>()

const requestType = ref<DsarRequestType | ''>('')
const notes = ref('')
const newEmail = ref('')
const newUsername = ref('')
const submitting = ref(false)
const showConfirmDeletion = ref(false)

const requestTypes: DsarRequestType[] = ['ACCESS', 'EXPORT', 'CORRECTION', 'DELETION']

const isCorrection = computed(() => requestType.value === 'CORRECTION')
const isDeletion = computed(() => requestType.value === 'DELETION')
const canSubmit = computed(() => requestType.value !== '')

function handleSubmit(): void {
  if (!requestType.value) return

  const payload: { type: DsarRequestType; notes?: string; correctionData?: CorrectionData } = {
    type: requestType.value,
  }

  if (notes.value.trim()) {
    payload.notes = notes.value.trim()
  }

  if (isCorrection.value && (newEmail.value.trim() || newUsername.value.trim())) {
    payload.correctionData = {
      newEmail: newEmail.value.trim() || null,
      newUsername: newUsername.value.trim() || null,
    }
  }

  emit('submit', payload)
}

function onConfirmDelete(): void {
  showConfirmDeletion.value = false
  handleSubmit()
}

function onTriggerSubmit(): void {
  if (isDeletion.value) {
    showConfirmDeletion.value = true
  } else {
    handleSubmit()
  }
}

defineExpose({ submitting })
</script>

<template>
  <div class="space-y-5">
    <div class="space-y-2">
      <label for="dsar-request-type" class="text-sm font-medium text-text-display">
        {{ t('settings.privacy.form.type.label') }}
      </label>
      <Select v-model="requestType" :aria-label="t('settings.privacy.form.type.label')">
        <SelectTrigger id="dsar-request-type" data-testid="dsar-type-select">
          <SelectValue :placeholder="t('settings.privacy.form.type.placeholder')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem
            v-for="type in requestTypes"
            :key="type"
            :value="type"
            :data-testid="`dsar-type-${type}`"
          >
            {{ t(`settings.privacy.form.type.${type}`) }}
          </SelectItem>
        </SelectContent>
      </Select>
    </div>

    <div v-if="isCorrection" class="space-y-4">
      <div class="space-y-2">
        <label for="dsar-new-email" class="text-sm font-medium text-text-display">
          {{ t('settings.privacy.form.correctionEmail') }}
        </label>
        <input
          id="dsar-new-email"
          v-model="newEmail"
          type="email"
          :placeholder="t('settings.privacy.form.correctionEmailPlaceholder')"
          class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
          data-testid="dsar-correction-email"
        />
      </div>
      <div class="space-y-2">
        <label for="dsar-new-username" class="text-sm font-medium text-text-display">
          {{ t('settings.privacy.form.correctionUsername') }}
        </label>
        <input
          id="dsar-new-username"
          v-model="newUsername"
          type="text"
          :placeholder="t('settings.privacy.form.correctionUsernamePlaceholder')"
          class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
          data-testid="dsar-correction-username"
        />
      </div>
    </div>

    <div class="space-y-2">
      <label for="dsar-notes" class="text-sm font-medium text-text-display">
        {{ t('settings.privacy.form.notes') }}
      </label>
      <textarea
        id="dsar-notes"
        v-model="notes"
        :placeholder="t('settings.privacy.form.notesPlaceholder')"
        class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none min-h-[80px] resize-y"
        data-testid="dsar-notes"
      />
    </div>

    <div class="flex gap-3">
      <Button
        type="button"
        :disabled="!canSubmit || submitting"
        :data-testid="isDeletion ? 'dsar-submit-deletion' : 'dsar-submit'"
        @click="onTriggerSubmit"
      >
        <template v-if="isDeletion">
          {{ t('settings.privacy.form.cancel') }}
        </template>
        <template v-else>
          {{ submitting ? t('settings.privacy.form.submitting') : t('settings.privacy.form.submit') }}
        </template>
      </Button>
    </div>

    <Dialog v-model:open="showConfirmDeletion">
      <DialogContent data-testid="dsar-deletion-confirm-dialog">
        <DialogHeader>
          <DialogTitle>{{ t('settings.privacy.form.confirmDeletionTitle') }}</DialogTitle>
          <DialogDescription>
            {{ t('settings.privacy.form.confirmDeletionDesc') }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose as-child>
            <Button variant="outline" data-testid="dsar-deletion-cancel">
              {{ t('settings.privacy.form.cancel') }}
            </Button>
          </DialogClose>
          <Button
            variant="destructive"
            data-testid="dsar-deletion-confirm"
            @click="onConfirmDelete"
          >
            {{ t('settings.privacy.form.confirmDeletionYes') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
