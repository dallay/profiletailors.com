<script setup lang="ts">
import { onMounted } from 'vue'
import { CheckCircle2, Loader2, TriangleAlert } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useLinkedInCallback } from '@modules/auth/application/useLinkedInCallback'

const { status, message, retryConnection, processCallback } = useLinkedInCallback()

onMounted(() => {
  void processCallback()
})
</script>

<template>
  <div class="mx-auto flex min-h-[60vh] max-w-2xl items-center justify-center py-12">
    <Card class="w-full border border-border-subtle bg-bg-surface">
      <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-6 text-center">
        <div class="mx-auto flex size-12 items-center justify-center rounded-2xl border border-border-visible bg-bg-primary text-text-display">
          <Loader2 v-if="status === 'loading'" class="size-5 animate-spin" />
          <CheckCircle2 v-else-if="status === 'success'" class="size-5 text-success" />
          <TriangleAlert v-else class="size-5 text-error" />
        </div>

        <CardTitle class="text-2xl font-light tracking-tight text-text-display">
          {{ status === 'success' ? $t('linkedinCallback.successTitle') : status === 'error' ? $t('linkedinCallback.errorTitle') : $t('linkedinCallback.loadingTitle') }}
        </CardTitle>
      </CardHeader>

      <CardContent class="mt-6 space-y-6 p-0 text-center">
        <output aria-live="polite" class="text-sm leading-6 text-text-secondary">
          {{ message }}
        </output>

        <div v-if="status === 'error'" class="flex flex-col justify-center gap-3 sm:flex-row">
          <Button type="button" @click="retryConnection">
            {{ $t('linkedinCallback.tryAgain') }}
          </Button>
          <Button as="a" href="/settings" variant="outline" type="button">
            {{ $t('linkedinCallback.backToSettings') }}
          </Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
