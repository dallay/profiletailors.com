<script setup lang="ts">
import { ref, watch, } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { useConsentStore } from '@modules/settings/infrastructure/consent.store'

const { t } = useI18n()
const store = useConsentStore()

const analyticsEnabled = ref(store.analyticsEnabled)
const open = ref(false)

// Determine dialog visibility: show when no valid consent OR forceOpen
function shouldShow(): boolean {
  return !store.hasValidConsent || store.forceOpen
}

open.value = shouldShow()

// Watch for forceOpen changes (e.g. from footer "Cookie Settings" link)
watch(
  () => store.forceOpen,
  (val) => {
    if (val) open.value = true
  },
)

// When the dialog is dismissed, reset forceOpen
watch(open, (val) => {
  if (!val && store.forceOpen) {
    store.closeSettings()
  }
})

function acceptAll(): void {
  store.saveConsent({ analytics: true, source: 'banner' })
  open.value = false
}

function rejectAll(): void {
  store.saveConsent({ analytics: false, source: 'banner' })
  open.value = false
}

function save(): void {
  store.saveConsent({ analytics: analyticsEnabled.value, source: 'banner' })
  open.value = false
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      data-testid="consent-banner"
      class="sm:max-w-md"
    >
      <DialogTitle class="label-mono text-[10px] text-text-display">
        {{ t('consent.banner.title') }}
      </DialogTitle>

      <DialogDescription class="text-sm leading-6 text-text-secondary">
        {{ t('consent.banner.description') }}
      </DialogDescription>

      <!-- Necessary cookies (always on, disabled) -->
      <div class="flex items-center justify-between rounded-2xl border border-border-subtle bg-bg-primary px-4 py-3">
        <div>
          <p class="text-sm font-medium text-text-display">
            {{ t('consent.categories.necessary') }}
          </p>
          <p class="text-xs leading-5 text-text-secondary">
            {{ t('consent.categories.necessaryDesc') }}
          </p>
        </div>
        <Switch :model-value="true" disabled />
      </div>

      <!-- Analytics cookies (user choice) -->
      <div class="flex items-center justify-between rounded-2xl border border-border-subtle bg-bg-primary px-4 py-3">
        <div>
          <p class="text-sm font-medium text-text-display">
            {{ t('consent.categories.analytics') }}
          </p>
          <p class="text-xs leading-5 text-text-secondary">
            {{ t('consent.categories.analyticsDesc') }}
          </p>
        </div>
        <Switch v-model="analyticsEnabled" />
      </div>

      <!-- Actions: equal-prominence buttons -->
      <div class="mt-2 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Button
          data-testid="reject-all-btn"
          variant="outline"
          size="sm"
          class="w-full sm:w-auto"
          @click="rejectAll"
        >
          {{ t('consent.banner.rejectAll') }}
        </Button>
        <Button
          data-testid="save-btn"
          variant="outline"
          size="sm"
          class="w-full sm:w-auto"
          @click="save"
        >
          {{ t('consent.banner.save') }}
        </Button>
        <Button
          data-testid="accept-all-btn"
          size="sm"
          class="w-full sm:w-auto"
          @click="acceptAll"
        >
          {{ t('consent.banner.acceptAll') }}
        </Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
