<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { usePrivacyStore } from '@modules/settings/infrastructure/privacy.store'
import type { DsarRequestType, CorrectionData } from '@modules/settings/infrastructure/privacy.store'
import DsarRequestForm from './components/DsarRequestForm.vue'
import DsarRequestList from './components/DsarRequestList.vue'

const { t } = useI18n()
const privacy = usePrivacyStore()

const submitSuccess = ref(false)
let submitSuccessTimeout: ReturnType<typeof setTimeout> | null = null

async function onSubmit(payload: {
  type: DsarRequestType
  notes?: string
  correctionData?: CorrectionData
}): Promise<void> {
  try {
    await privacy.submitRequest(payload)
    submitSuccess.value = true
    submitSuccessTimeout = setTimeout(() => {
      submitSuccess.value = false
      submitSuccessTimeout = null
    }, 5000)
  } catch {
    // Error is handled by the store; the error state displays via the list or inline.
  }
}

onMounted(() => {
  privacy.fetchRequests().catch(() => undefined)
})
</script>

<template>
  <Card
    data-testid="settings-privacy-panel"
    class="border border-border-subtle bg-bg-surface p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]"
  >
    <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-5">
      <CardTitle class="label-mono text-[10px] text-text-display">
        {{ t('settings.privacy.title') }}
      </CardTitle>
      <p class="max-w-lg text-sm leading-6 text-text-secondary">
        {{ t('settings.privacy.description') }}
      </p>
    </CardHeader>

    <CardContent class="mt-6 space-y-8 p-0">
      <output
        v-if="submitSuccess"
        class="block rounded-2xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success"
        data-testid="dsar-submit-success"
      >
        {{ t('settings.privacy.form.success') }}
      </output>

      <div>
        <DsarRequestForm @submit="onSubmit" />
      </div>

      <div v-if="privacy.error" class="text-sm text-error" role="alert" data-testid="dsar-error">
        {{ privacy.error }}
      </div>

      <div>
        <h3 class="font-mono text-[10px] font-bold uppercase tracking-[0.16em] text-text-display mb-4">
          {{ t('settings.privacy.list.title') }}
        </h3>
        <DsarRequestList
          :requests="privacy.requests"
          :loading="privacy.loading"
        />
      </div>
    </CardContent>
  </Card>
</template>
