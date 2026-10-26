<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Heart, MessageCircle, Repeat2, Send } from '@lucide/vue'
import { proxyImageUrl } from '@modules/auth/infrastructure/auth-api'
import type { ThreadsPreviewModel } from './post-preview.types'

const props = defineProps<{
  preview: ThreadsPreviewModel
}>()

const { t } = useI18n()

const avatarFailed = ref(false)

const showAvatar = computed(() => !!props.preview.authorAvatarUrl && !avatarFailed.value)

function onAvatarError(): void {
  avatarFailed.value = true
}

watch(
  () => props.preview.authorAvatarUrl,
  () => {
    avatarFailed.value = false
  },
)
</script>

<template>
  <div
    data-testid="threads-preview"
    class="w-full max-w-[360px] overflow-hidden rounded-xl border border-border-subtle bg-bg-primary font-sans text-xs text-text-display shadow-md"
  >
    <div class="flex gap-3 p-3.5">
      <img
        v-if="showAvatar"
        :src="proxyImageUrl(preview.authorAvatarUrl ?? '')"
        :alt="`${preview.authorName} avatar`"
        data-testid="threads-preview-avatar"
        class="size-10 rounded-full border border-border-subtle object-cover"
        @error="onAvatarError"
      >
      <div
        v-else
        data-testid="threads-preview-avatar-fallback"
        class="flex size-10 shrink-0 items-center justify-center rounded-full border border-border-subtle bg-bg-surface font-mono text-[11px] font-bold uppercase text-text-display"
      >
        {{ preview.authorInitials }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1.5">
          <p class="truncate text-[13px] font-semibold text-text-display">
            {{ preview.authorName }}
          </p>
        </div>
        <p class="truncate text-[11px] text-text-secondary">
          {{ preview.authorHandle }}
        </p>
        <p class="mt-0.5 text-[10px] text-text-secondary">
          {{ t('composer.previewMeta.justNow') }}
        </p>
      </div>
    </div>

    <div class="px-3.5 pb-3.5 text-[13px] leading-relaxed whitespace-pre-wrap break-words text-text-display [overflow-wrap:anywhere]">
      <span v-if="preview.text.trim().length === 0" class="text-text-secondary italic">
        {{ preview.placeholderText }}
      </span>
      <p v-else data-testid="threads-preview-text">
        {{ preview.text }}
      </p>
    </div>

    <div
      v-if="preview.media"
      class="flex max-h-[220px] items-center justify-center overflow-hidden border-t border-border-subtle bg-bg-surface"
      data-testid="threads-preview-media"
    >
      <img
        v-if="preview.media.kind === 'image' && preview.media.url"
        :src="preview.media.url"
        :alt="preview.media.alt"
        class="h-auto max-h-[220px] w-full object-contain"
      >
      <div
        v-else
        class="flex w-full items-center justify-between gap-3 px-4 py-4 text-left"
      >
        <div class="min-w-0 flex-1">
          <p class="font-mono text-[10px] uppercase tracking-[0.2em] text-text-secondary">
            {{ t('composer.previewMeta.mediaAttachment') }}
          </p>
          <p class="mt-1 text-[12px] font-medium text-text-display">
            {{ preview.media.name ?? t('composer.previewMeta.videoAttachment') }}
          </p>
        </div>
      </div>
    </div>

    <div class="flex justify-around border-t border-border-subtle px-1 py-1 text-text-secondary">
      <button
        type="button"
        class="inline-flex items-center rounded px-2 py-2 hover:bg-bg-surface"
        :aria-label="t('composer.previewMeta.like')"
      >
        <Heart class="size-3.5" />
      </button>
      <button
        type="button"
        class="inline-flex items-center rounded px-2 py-2 hover:bg-bg-surface"
        :aria-label="t('composer.previewMeta.comment')"
      >
        <MessageCircle class="size-3.5" />
      </button>
      <button
        type="button"
        class="inline-flex items-center rounded px-2 py-2 hover:bg-bg-surface"
        :aria-label="t('composer.previewMeta.repost')"
      >
        <Repeat2 class="size-3.5" />
      </button>
      <button
        type="button"
        class="inline-flex items-center rounded px-2 py-2 hover:bg-bg-surface"
        :aria-label="t('composer.previewMeta.send')"
      >
        <Send class="size-3.5" />
      </button>
    </div>
  </div>
</template>
