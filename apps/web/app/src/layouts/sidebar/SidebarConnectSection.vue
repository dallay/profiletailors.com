<script setup lang="ts">
import { computed } from 'vue'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import {
  getProviderPresentation,
  type ProviderCatalogItem,
} from '@shared/lib/provider-presentation'

const props = defineProps<{
  providers: ProviderCatalogItem[]
}>()

const emit = defineEmits<(e: 'connect', provider: ProviderCatalogItem) => void>()

const visibleProviders = computed(() =>
  props.providers.filter((provider) => provider.state !== 'HIDDEN'),
)
</script>

<template>
  <div v-if="visibleProviders.length" class="mt-3 space-y-1.5 border-t border-border-subtle pt-3">
    <p class="px-2 font-mono text-[9px] uppercase tracking-[0.18em] text-text-secondary/80 group-data-[collapsible=icon]:hidden">
      Connect
    </p>

    <template v-for="provider in visibleProviders" :key="provider.provider">
      <button
        v-if="provider.state === 'AVAILABLE'"
        :data-testid="`connect-provider-${provider.provider}`"
        class="flex w-full items-center gap-3 rounded-xl border border-transparent px-2.5 py-2 text-left text-[13px] text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
        type="button"
        @click="emit('connect', provider)"
      >
        <span class="flex size-5 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-primary text-text-display">
          <SocialProviderIcon :provider="provider.provider" />
        </span>
        <span class="sr-only">{{ getProviderPresentation(provider.provider).label }} - Connect</span>
        <span class="min-w-0 flex-1 truncate group-data-[collapsible=icon]:hidden">{{ getProviderPresentation(provider.provider).label }}</span>
        <span class="font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary/80 group-data-[collapsible=icon]:hidden">+ Connect</span>
      </button>

      <div
        v-else
        :data-testid="`locked-provider-${provider.provider}`"
        class="flex w-full items-center gap-3 rounded-xl border border-border-subtle px-2.5 py-2 text-[13px] text-text-secondary group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
      >
        <span class="flex size-5 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-primary text-text-secondary">
          <SocialProviderIcon :provider="provider.provider" />
        </span>
        <span class="min-w-0 flex-1 truncate group-data-[collapsible=icon]:hidden">{{ getProviderPresentation(provider.provider).label }}</span>
        <span class="font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary/80 group-data-[collapsible=icon]:hidden">{{ provider.reason }}</span>
      </div>
    </template>
  </div>
</template>
