<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { useConsent } from './useConsent'

const { t } = useI18n()
const { hasValidConsent, analyticsEnabled, acceptAll, rejectAll, save } = useConsent('banner')
const customizeOpen = ref(false)
const analyticsEnabledDraft = ref(analyticsEnabled.value)

function openCustomize(): void {
  analyticsEnabledDraft.value = analyticsEnabled.value
  customizeOpen.value = true
}

function back(): void {
  analyticsEnabledDraft.value = analyticsEnabled.value
  customizeOpen.value = false
}

function saveCustomize(): void {
  save(analyticsEnabledDraft.value)
}
</script>

<template>
  <aside
    v-if="!hasValidConsent"
    data-testid="consent-banner"
    class="fixed inset-x-0 bottom-0 z-40 px-4 pt-4 pb-[calc(env(safe-area-inset-bottom)+1rem)] sm:inset-x-auto sm:right-6 sm:bottom-6 sm:w-[min(40rem,calc(100vw-3rem))] sm:px-0 sm:pb-[calc(env(safe-area-inset-bottom)+1.5rem)]"
    aria-labelledby="consent-banner-title"
    aria-describedby="consent-banner-description"
  >
    <div class="overflow-hidden rounded-t-2xl border border-border-visible bg-bg-surface shadow-2xl sm:rounded-2xl">
      <div class="space-y-4 p-5 sm:p-6">
        <h2 id="consent-banner-title" class="label-mono text-[10px] text-text-display">
          {{ t('consent.banner.title') }}
        </h2>
        <p id="consent-banner-description" class="text-sm leading-6 text-text-secondary">
          {{ t('consent.banner.description') }}
        </p>

        <div v-if="!customizeOpen" class="grid gap-2 sm:grid-cols-3">
          <Button
            data-testid="reject-all-btn"
            type="button"
            variant="outline"
            class="min-h-11 w-full"
            @click="rejectAll"
          >
            {{ t('consent.banner.rejectAll') }}
          </Button>
          <Button
            data-testid="customize-btn"
            type="button"
            variant="outline"
            class="min-h-11 w-full"
            @click="openCustomize"
          >
            {{ t('consent.banner.customize') }}
          </Button>
          <Button
            data-testid="accept-all-btn"
            type="button"
            variant="default"
            class="min-h-11 w-full"
            @click="acceptAll"
          >
            {{ t('consent.banner.acceptAll') }}
          </Button>
        </div>

        <div v-else data-testid="customize-panel" class="space-y-3">
          <div class="flex items-center justify-between gap-4 rounded-xl border border-border-subtle bg-bg-primary px-4 py-3">
            <div>
              <p id="consent-label-necessary" class="text-sm font-medium text-text-display">
                {{ t('consent.categories.necessary') }}
              </p>
              <p class="text-xs leading-5 text-text-secondary">
                {{ t('consent.categories.necessaryDesc') }}
              </p>
            </div>
            <Switch
              data-testid="necessary-toggle"
              :model-value="true"
              disabled
              aria-labelledby="consent-label-necessary"
            />
          </div>

          <div class="flex items-center justify-between gap-4 rounded-xl border border-border-subtle bg-bg-primary px-4 py-3">
            <div>
              <p id="consent-label-analytics" class="text-sm font-medium text-text-display">
                {{ t('consent.categories.analytics') }}
              </p>
              <p class="text-xs leading-5 text-text-secondary">
                {{ t('consent.categories.analyticsDesc') }}
              </p>
            </div>
            <Switch
              data-testid="analytics-toggle"
              v-model="analyticsEnabledDraft"
              aria-labelledby="consent-label-analytics"
            />
          </div>

          <div class="grid gap-2 sm:grid-cols-2">
            <Button
              data-testid="back-btn"
              type="button"
              variant="outline"
              class="min-h-11 w-full"
              @click="back"
            >
              {{ t('consent.banner.back') }}
            </Button>
            <Button
              data-testid="save-btn"
              type="button"
              variant="default"
              class="min-h-11 w-full"
              @click="saveCustomize"
            >
              {{ t('consent.banner.save') }}
            </Button>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>
