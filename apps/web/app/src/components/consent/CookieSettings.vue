<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { useConsent } from './useConsent'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<(e: 'update:open', val: boolean) => void>()

const { t } = useI18n()
const { analyticsEnabled, acceptAll: saveAll, rejectAll: rejectAllConsent, save: saveConsent } = useConsent('settings-panel')
const consentAnalyticsEnabled = ref(analyticsEnabled.value)

watch(
  () => props.open,
  (val) => {
    if (val) {
      consentAnalyticsEnabled.value = analyticsEnabled.value
    }
  },
)

function acceptAll() {
  consentAnalyticsEnabled.value = true
  saveAll()
  emit('update:open', false)
}

function rejectAll() {
  consentAnalyticsEnabled.value = false
  rejectAllConsent()
  emit('update:open', false)
}

function save() {
  saveConsent(consentAnalyticsEnabled.value)
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-md">
      <DialogTitle>{{ t('consent.banner.title') }}</DialogTitle>
      <DialogDescription>{{ t('consent.banner.description') }}</DialogDescription>

      <div class="flex items-center justify-between rounded-2xl border border-border-subtle bg-bg-primary px-4 py-3">
        <div>
          <p id="consent-label-necessary" class="text-sm font-medium text-text-display">
            {{ t('consent.categories.necessary') }}
          </p>
          <p class="text-xs leading-5 text-text-secondary">
            {{ t('consent.categories.necessaryDesc') }}
          </p>
        </div>
        <Switch :model-value="true" disabled :aria-labelledby="'consent-label-necessary'" />
      </div>

      <div class="flex items-center justify-between rounded-2xl border border-border-subtle bg-bg-primary px-4 py-3">
        <div>
          <p id="consent-label-analytics" class="text-sm font-medium text-text-display">
            {{ t('consent.categories.analytics') }}
          </p>
          <p class="text-xs leading-5 text-text-secondary">
            {{ t('consent.categories.analyticsDesc') }}
          </p>
        </div>
        <Switch v-model="consentAnalyticsEnabled" :aria-labelledby="'consent-label-analytics'" />
      </div>

      <div class="mt-2 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Button variant="outline" size="sm" class="w-full sm:w-auto" @click="rejectAll">
          {{ t('consent.banner.rejectAll') }}
        </Button>
        <Button variant="outline" size="sm" class="w-full sm:w-auto" @click="save">
          {{ t('consent.banner.save') }}
        </Button>
        <Button size="sm" class="w-full sm:w-auto" @click="acceptAll">
          {{ t('consent.banner.acceptAll') }}
        </Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
