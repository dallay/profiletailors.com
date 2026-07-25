<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { getProviderPresentation } from '@shared/lib/provider-presentation'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import { proxyImageUrl } from '@modules/auth/infrastructure/auth-api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const { t } = useI18n()
const route = useRoute()
const publishing = usePublishingStore()

const connectingLinkedIn = ref(false)
const connectError = ref<string | null>(null)

const linkedinConnected = computed(
  () => route.query.connected === 'linkedin' && route.query.provider === 'linkedin',
)

const channelsPanelFocused = computed(() => route.query.panel === 'channels')

async function connectLinkedInProfile() {
  connectingLinkedIn.value = true
  connectError.value = null
  try {
    await publishing.connectLinkedInPersonalProfile()
  } catch (err) {
    connectError.value = err instanceof Error ? err.message : t('channels.connectionFailed')
  } finally {
    connectingLinkedIn.value = false
  }
}
</script>

<template>
  <Card
    data-testid="settings-channels-panel"
    class="border border-border-subtle bg-bg-surface p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] transition-colors"
    :class="channelsPanelFocused ? 'shadow-[0_0_0_1px_rgba(255,255,255,0.12)]' : ''"
  >
    <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-5">
      <CardTitle class="label-mono text-[10px] text-text-display">
        {{ $t('channels.title') }}
      </CardTitle>
      <p class="max-w-lg text-sm leading-6 text-text-secondary">
        {{ $t('channels.connectLinkedInProfileDesc') }}
      </p>
    </CardHeader>

    <CardContent class="mt-6 space-y-5 p-0">
      <p
        v-if="linkedinConnected"
        class="rounded-2xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success"
      >
        {{ $t('linkedinCallback.successMessage') }}
      </p>

      <p
        v-if="publishing.channelsLoading"
        class="font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary"
      >
        {{ $t('channels.loading') }}
      </p>

      <div v-if="publishing.channels.length" class="space-y-3">
        <div
          v-for="channel in publishing.channels"
          :key="channel.id"
          class="flex items-center gap-4 rounded-2xl border border-border-subtle bg-bg-primary px-4 py-4"
          data-testid="settings-connected-channel"
        >
          <img
            v-if="channel.avatarUrl"
            :src="proxyImageUrl(channel.avatarUrl)"
            :alt="`${channel.name} avatar`"
            class="size-11 rounded-full border border-border-visible object-cover"
          >
          <div
            v-else
            class="flex size-11 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-surface font-mono text-[10px] font-bold uppercase text-text-display"
          >
            <SocialProviderIcon :provider="channel.provider" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-text-display">{{ channel.name }}</p>
            <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
              {{ getProviderPresentation(channel.provider).label }} · {{ channel.accountId }}
            </p>
          </div>
          <span
            class="inline-flex items-center gap-1.5 rounded-full border border-border-visible px-2.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em]"
            :class="channel.status === 'ACTIVE' ? 'text-success' : 'text-error'"
          >
            <span
              aria-hidden="true"
              class="size-1.5 rounded-full"
              :class="channel.status === 'ACTIVE' ? 'bg-success' : 'bg-error'"
            />
            {{ channel.status === 'ACTIVE' ? $t('channels.active') : $t('channels.needsReconnect') }}
          </span>
        </div>
      </div>

      <div
        v-else-if="!publishing.isLinkedInConfigured"
        class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/50 p-5"
      >
        <p class="text-sm font-medium text-text-display">{{ $t('channels.notConfigured') }}</p>
        <p class="mt-1 text-xs leading-5 text-text-secondary">{{ $t('channels.notConfiguredDesc') }}</p>
      </div>

      <div
        v-else
        class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/50 p-5"
      >
        <p class="text-sm font-medium text-text-display">{{ $t('channels.noChannels') }}</p>
        <p class="mt-1 text-xs leading-5 text-text-secondary">{{ $t('channels.connectLinkedInProfileDesc') }}</p>
        <Button
          v-if="publishing.isLinkedInConfigured"
          type="button"
          class="mt-4"
          :disabled="connectingLinkedIn"
          @click="connectLinkedInProfile"
        >
          {{ connectingLinkedIn ? $t('channels.connectingLinkedIn') : $t('channels.connectLinkedInProfile') }}
        </Button>
      </div>

      <p v-if="connectError || publishing.channelsError" role="alert" class="text-sm text-error">
        {{ connectError || publishing.channelsError }}
      </p>
    </CardContent>
  </Card>
</template>
