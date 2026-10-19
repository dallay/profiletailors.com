<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Channel } from '@modules/publishing/domain/channel'
import { channelInitials } from '@modules/publishing/application/channel-initials'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'

const props = defineProps<{
  channels: Channel[]
  selectedChannelIds: string[]
  isEditMode: boolean
}>()

const emit = defineEmits<{
  toggle: [channelId: string]
  add: []
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

function handleToggle(channelId: string) {
  if (props.isEditMode) return
  emit('toggle', channelId)
}

function handleAdd() {
  if (props.isEditMode) return
  emit('add')
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
    <div class="flex flex-wrap items-center gap-2">
      <button
        v-for="ch in activeChannels"
        :key="ch.id"
        type="button"
        data-testid="channel-button"
        :disabled="isEditMode"
        :title="ch.name"
        class="relative flex size-11 shrink-0 items-center justify-center rounded-xl border transition-all"
        :class="[
          selectedChannelIds.includes(ch.id)
            ? 'border-text-display'
            : 'border-border-visible hover:border-text-display/50',
          isEditMode ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer',
        ]"
        :data-edit-disabled="isEditMode ? 'true' : 'false'"
        :data-channel-id="ch.id"
        :data-channel-name="ch.name"
        :data-channel-provider="ch.provider"
        :aria-label="ch.name"
        :aria-pressed="selectedChannelIds.includes(ch.id)"
        @click="handleToggle(ch.id)"
      >
        <img
          v-if="shouldShowAvatar(ch.id, ch.avatarUrl)"
          :src="ch.avatarUrl"
          :alt="ch.name ? `${ch.name} avatar` : 'Channel avatar'"
          class="size-11 rounded-xl object-cover"
          @error="onAvatarError(ch.id)"
        />
        <span
          v-else
          data-testid="channel-avatar-fallback"
          class="flex size-11 items-center justify-center rounded-xl bg-bg-primary font-mono text-[12px] font-bold uppercase text-text-display"
        >
          {{ channelInitials(ch.name) }}
        </span>
        <span
          data-testid="channel-provider-badge"
          aria-hidden="true"
          class="absolute -bottom-1 -right-1 flex size-5 items-center justify-center rounded-full border border-border-subtle bg-bg-surface p-[2px] text-text-display"
        >
          <SocialProviderIcon :provider="ch.provider" />
        </span>
      </button>

      <button
        type="button"
        data-testid="channel-add"
        title="Connect another channel"
        class="flex size-11 shrink-0 items-center justify-center rounded-xl border border-dashed border-border-visible text-text-secondary transition-colors hover:border-text-display hover:text-text-display"
        @click="handleAdd"
      >
        <span class="text-lg font-light leading-none">+</span>
      </button>
    </div>
  </div>
</template>

