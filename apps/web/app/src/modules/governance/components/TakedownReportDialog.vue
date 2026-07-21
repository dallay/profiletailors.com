<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { reportTakedown } from '@modules/governance/services/governance-api'

const props = defineProps<{
  assetId: string
}>()

const emit = defineEmits<{
  reported: []
}>()

const { t } = useI18n()
const auth = useAuthStore()

const isOpen = ref(false)
const isSubmitting = ref(false)
const error = ref<string | null>(null)

const reason = ref('')
const mediaReferenceUrl = ref('')
const reporterEmail = ref(auth.user?.email ?? '')

function resetForm() {
  reason.value = ''
  mediaReferenceUrl.value = ''
  reporterEmail.value = auth.user?.email ?? ''
  error.value = null
}

function handleOpenChange(open: boolean) {
  isOpen.value = open
  if (open) {
    resetForm()
  }
}

async function handleSubmit() {
  if (!reason.value.trim()) {
    error.value = t('governance.takedown.report.errors.reasonRequired')
    return
  }

  isSubmitting.value = true
  error.value = null

  try {
    await reportTakedown({
      assetId: props.assetId,
      reason: reason.value.trim(),
      mediaReferenceUrl: mediaReferenceUrl.value.trim() || undefined,
    })
    isOpen.value = false
    emit('reported')
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('governance.takedown.report.errors.submitFailed')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <Dialog :open="isOpen" @update:open="handleOpenChange">
    <DialogTrigger as-child>
      <slot>
        <Button type="button" variant="outline" size="sm">
          {{ $t('governance.takedown.report.action') }}
        </Button>
      </slot>
    </DialogTrigger>
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{ $t('governance.takedown.report.title') }}</DialogTitle>
        <DialogDescription>
          {{ $t('governance.takedown.report.description') }}
        </DialogDescription>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="handleSubmit">
        <div class="space-y-2">
          <Label for="takedown-reason">{{ $t('governance.takedown.report.reasonLabel') }}</Label>
          <Textarea
            id="takedown-reason"
            v-model="reason"
            :placeholder="$t('governance.takedown.report.reasonPlaceholder')"
            :rows="4"
            :aria-label="$t('governance.takedown.report.reasonLabel')"
          />
        </div>

        <div class="space-y-2">
          <Label for="takedown-email">{{ $t('governance.takedown.report.emailLabel') }}</Label>
          <Input
            id="takedown-email"
            v-model="reporterEmail"
            type="email"
            readonly
            :aria-label="$t('governance.takedown.report.emailLabel')"
          />
          <p class="text-xs text-text-secondary">
            {{ $t('governance.takedown.report.emailHint') }}
          </p>
        </div>

        <div class="space-y-2">
          <Label for="takedown-url">{{ $t('governance.takedown.report.urlLabel') }}</Label>
          <Input
            id="takedown-url"
            v-model="mediaReferenceUrl"
            type="url"
            :placeholder="$t('governance.takedown.report.urlPlaceholder')"
            :aria-label="$t('governance.takedown.report.urlLabel')"
          />
        </div>

        <div v-if="error" class="rounded-lg border border-error/30 bg-error/10 px-3 py-2 text-sm text-error">
          {{ error }}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="isOpen = false">
            {{ $t('workspace.cancel') }}
          </Button>
          <Button type="submit" :disabled="isSubmitting">
            {{ isSubmitting ? $t('governance.takedown.report.submitting') : $t('governance.takedown.report.submitAction') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
