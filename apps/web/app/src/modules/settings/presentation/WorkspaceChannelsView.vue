<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePublishingStore, type Channel } from '@modules/publishing/infrastructure/publishing.store'
import {
  getProviderPresentation,
  PROVIDER_ACTIONS,
  type ProviderCatalogItem,
} from '@shared/lib/provider-presentation'
import { Radio } from 'lucide-vue-next'

const { t } = useI18n()
const publishingStore = usePublishingStore()

const channels = computed<Channel[]>(() => publishingStore.channels as Channel[])
const providerCatalog = computed<ProviderCatalogItem[]>(() => publishingStore.providerCatalog)

async function handleConnectProvider(provider: ProviderCatalogItem) {
  if (
    provider.state !== 'AVAILABLE' ||
    getProviderPresentation(provider.provider).action !== PROVIDER_ACTIONS.CONNECT_LINKEDIN_PERSONAL_PROFILE
  ) {
    return
  }

  try {
    await publishingStore.connectLinkedInPersonalProfile()
  } catch (err) {
    console.error('Failed to connect provider', err)
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-text-display">
        {{ t('settings.headers.channelsTitle') }}
      </h1>
      <p class="text-sm text-text-secondary mt-1">
        {{ t('settings.headers.channelsSubtitle') }}
      </p>
    </div>

    <!-- Connected Channels Card -->
    <div class="rounded-xl border border-border-subtle bg-bg-surface p-6 space-y-6 shadow-sm">
      <h2 class="text-xs font-semibold text-text-muted uppercase tracking-wider">
        {{ t('settings.workspaceChannels.connectedChannelsTitle') }}
      </h2>

      <!-- Empty state -->
      <div v-if="!channels.length" class="p-6 text-center text-text-muted text-sm border border-dashed border-border-subtle rounded-xl">
        {{ t('settings.workspaceChannels.noConnectedChannels') }}
      </div>

      <!-- Channels List -->
      <div v-else class="space-y-4 divide-y divide-border-subtle/50">
        <div
          v-for="(channel, idx) in channels"
          :key="channel.id"
          :class="['flex items-center justify-between gap-4', idx > 0 ? 'pt-4' : '']"
        >
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-10 h-10 rounded-full bg-bg-subtle border border-border-subtle flex items-center justify-center shrink-0 overflow-hidden">
              <img
                v-if="channel.avatar"
                :src="channel.avatar"
                :alt="channel.name"
                class="w-full h-full object-cover"
              />
              <Radio v-else class="w-5 h-5 text-text-muted" />
            </div>

            <div class="min-w-0">
              <p class="text-sm font-semibold text-text-display truncate">
                {{ channel.name }}
              </p>
              <p class="text-xs text-text-secondary capitalize truncate">
                {{ channel.provider }} • {{ channel.accountType || 'Personal profile' }}
              </p>
            </div>
          </div>

          <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 shrink-0">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
            <span>{{ t('settings.workspaceChannels.activeStatus') }}</span>
          </span>
        </div>
      </div>
    </div>

    <!-- Available Providers Card -->
    <div class="rounded-xl border border-border-subtle bg-bg-surface p-6 space-y-6 shadow-sm">
      <h2 class="text-xs font-semibold text-text-muted uppercase tracking-wider">
        {{ t('settings.workspaceChannels.availableProvidersTitle') }}
      </h2>

      <div v-if="!providerCatalog.length" class="p-6 text-center text-text-muted text-sm border border-dashed border-border-subtle rounded-xl">
        {{ t('settings.workspaceChannels.noAvailableProviders') }}
      </div>

      <div v-else class="space-y-4 divide-y divide-border-subtle/50">
        <div
          v-for="(provider, idx) in providerCatalog"
          :key="provider.provider"
          :class="['flex items-center justify-between gap-4', idx > 0 ? 'pt-4' : '']"
        >
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-10 h-10 rounded-xl bg-bg-subtle border border-border-subtle flex items-center justify-center shrink-0">
              <Radio class="w-5 h-5 text-text-muted" />
            </div>

            <div class="min-w-0">
              <p class="text-sm font-semibold text-text-display capitalize">
                {{ provider.provider }}
              </p>
              <p class="text-xs text-text-secondary truncate">
                Connect an account from {{ provider.provider }}.
              </p>
            </div>
          </div>

          <button
            type="button"
            :disabled="provider.state !== 'AVAILABLE'"
            class="px-4 py-2 rounded-lg bg-primary-600 text-white text-xs font-medium hover:bg-primary-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shrink-0"
            @click="handleConnectProvider(provider)"
          >
            {{ t('settings.workspaceChannels.connectBtn') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
