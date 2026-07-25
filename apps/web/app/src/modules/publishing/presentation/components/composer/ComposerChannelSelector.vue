<script setup lang="ts">
import { ref } from 'vue'
import { Check, X } from '@lucide/vue'
import { proxyImageUrl } from '@modules/auth/infrastructure/auth-api'
import type { Channel } from '@modules/publishing/infrastructure/publishing.store'

const props = defineProps<{
  channels: Channel[]
  modelValue: string | null
  isEditMode: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', channelId: string): void
}>()

const avatarLoadFailed = ref<Record<string, boolean>>({})

function onChannelAvatarError(channelId: string) {
  avatarLoadFailed.value[channelId] = true
}

function shouldShowChannelAvatar(channelId: string, avatarUrl?: string): boolean {
  return !!(avatarUrl && !avatarLoadFailed.value[channelId])
}
</script>

<template>
  <div class="space-y-2">
    <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
      {{ $t('dashboard.selectChannels') }}
    </span>
    <div class="flex flex-wrap gap-2 items-center">
      <button
        v-for="ch in channels"
        :key="ch.id"
        type="button"
        :disabled="isEditMode"
        class="relative flex items-center gap-2 border rounded-full px-3 py-1.5 font-mono text-[10px] tracking-wide transition-all"
        :class="[
          modelValue === ch.id
            ? 'border-text-display bg-bg-primary text-text-display font-bold'
            : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary/50',
          isEditMode ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer',
        ]"
        :data-edit-disabled="isEditMode ? 'true' : 'false'"
        @click="isEditMode ? undefined : emit('update:modelValue', ch.id)"
      >
        <img
          v-if="shouldShowChannelAvatar(ch.id, ch.avatarUrl)"
          :src="proxyImageUrl(ch.avatarUrl ?? '')"
          :alt="`${ch.name} avatar`"
          class="size-4.5 rounded-full object-cover border border-border-subtle"
          @error="onChannelAvatarError(ch.id)"
        />
        <span
          v-else
          class="flex size-4.5 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-[7px] font-bold uppercase text-text-display"
        >
          {{ ch.provider === 'linkedin' ? 'in' : ch.provider.charAt(0) }}
        </span>
        <span class="max-w-[120px] truncate">{{ ch.name }}</span>
        <span
          class="flex size-3.5 shrink-0 items-center justify-center rounded-full text-[8px] font-bold text-bg-primary"
          :class="modelValue === ch.id ? 'bg-text-display' : 'bg-border-visible text-text-secondary'"
        >
          <component :is="modelValue === ch.id ? Check : X" class="size-2" />
        </span>
      </button>

      <button
        type="button"
        class="flex size-8 items-center justify-center rounded-full border border-dashed border-border-visible text-text-secondary hover:text-text-display hover:border-text-display bg-transparent transition-colors cursor-pointer"
        title="Connect another channel"
      >
        <span class="text-base font-light">+</span>
      </button>
    </div>
  </div>
</template>
