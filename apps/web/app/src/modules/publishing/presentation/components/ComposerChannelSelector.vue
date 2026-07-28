<script setup lang="ts">
import { ref, computed } from 'vue'
import { Check, X } from '@lucide/vue'
import type { ChannelShape } from '@modules/publishing/infrastructure/publishing.store'

const props = defineProps<{
  channels: ChannelShape[]
  selectedChannelId: string | null
  isEditMode: boolean
}>()

const emit = defineEmits<{
  select: [channelId: string]
}>()

const avatarLoadFailed = ref<Record<string, boolean>>({})

const activeChannels = computed(() =>
  props.channels.filter((ch) => ch.status === 'ACTIVE'),
)

function onAvatarError(channelId: string) {
  avatarLoadFailed.value[channelId] = true
}

function shouldShowAvatar(channelId: string, avatarUrl?: string | null): boolean {
  return !!(avatarUrl && !avatarLoadFailed.value[channelId])
}

function handleSelect(channelId: string) {
  if (props.isEditMode) return
  if (channelId === props.selectedChannelId) return
  emit('select', channelId)
}
</script>

<template>
  <div class="space-y-2">
    <span
      data-testid="channel-selector"
      class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block"
    >
      {{ $t('dashboard.selectChannels') }}
    </span>
    <div class="flex flex-wrap gap-2 items-center">
      <button
        v-for="ch in activeChannels"
        :key="ch.id"
        type="button"
        :disabled="isEditMode"
        class="relative flex items-center gap-2 border rounded-full px-3 py-1.5 font-mono text-[10px] tracking-wide transition-all"
        :class="[
          selectedChannelId === ch.id
            ? 'border-text-display bg-bg-primary text-text-display font-bold'
            : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary/50',
          isEditMode ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer',
        ]"
        :data-edit-disabled="isEditMode ? 'true' : 'false'"
        @click="handleSelect(ch.id)"
      >
        <img
          v-if="shouldShowAvatar(ch.id, ch.avatarUrl)"
          :src="ch.avatarUrl"
          :alt="`${ch.name} avatar`"
          class="size-4.5 rounded-full object-cover border border-border-subtle"
          @error="onAvatarError(ch.id)"
        />
        <span
          v-else
          data-testid="channel-avatar-fallback"
          class="flex size-4.5 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-[7px] font-bold uppercase text-text-display"
        >
          {{ ch.provider === 'linkedin' ? 'in' : ch.provider.charAt(0) }}
        </span>
        <span class="max-w-[120px] truncate">{{ ch.name }}</span>
        <span
          class="flex size-3.5 shrink-0 items-center justify-center rounded-full text-[8px] font-bold text-bg-primary"
          :class="selectedChannelId === ch.id ? 'bg-text-display' : 'bg-border-visible text-text-secondary'"
        >
          <component
            :is="selectedChannelId === ch.id ? Check : X"
            :data-testid="selectedChannelId === ch.id ? 'channel-selected-icon' : 'channel-deselected-icon'"
            class="size-2"
          />
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
