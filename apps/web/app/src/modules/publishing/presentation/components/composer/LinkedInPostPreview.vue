<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Globe, MessageCircle, Repeat2, Send, ThumbsUp } from '@lucide/vue'
import { proxyImageUrl } from '@modules/auth/infrastructure/auth-api'
import type { LinkedInPreviewModel } from './post-preview.types'

const props = defineProps<{
  preview: LinkedInPreviewModel
}>()

const { t } = useI18n()

const LINKEDIN_PREVIEW_CLAMP_CLASS = 'preview-text-clamp'
const LINKEDIN_PREVIEW_MAX_LENGTH = 240
const LINE_BREAK_WEIGHT = 18
const LONG_WORD_WEIGHT = 12
const MAX_WORD_LENGTH = 24

function estimateVisualLength(text: string): number {
  return text.split('').reduce((total, character) => {
    if (character === '\n') {
      return total + LINE_BREAK_WEIGHT
    }

    if (character === '\t') {
      return total + 4
    }

    return total + 1
  }, 0)
}

function normalizeTruncatedText(text: string): string {
  return text.trimEnd()
}

const textLengthScore = computed(() => {
  const whitespaceAdjustedLength = estimateVisualLength(props.preview.text)
  const longWordBonus = props.preview.text
    .split(/\s+/u)
    .filter((word) => word.length >= MAX_WORD_LENGTH)
    .length * LONG_WORD_WEIGHT

  return whitespaceAdjustedLength + longWordBonus
})

const isTruncated = computed(() => textLengthScore.value > LINKEDIN_PREVIEW_MAX_LENGTH)

const truncatedText = computed(() => {
  if (!isTruncated.value) {
    return props.preview.text
  }

  return normalizeTruncatedText(props.preview.text)
})
</script>

<template>
  <div class="w-full max-w-[360px] overflow-hidden rounded-xl border border-border-subtle bg-bg-primary font-sans text-xs text-text-display shadow-md">
    <div class="flex gap-3 p-3.5">
      <img
        v-if="preview.authorAvatarUrl"
        :src="proxyImageUrl(preview.authorAvatarUrl)"
        :alt="`${preview.authorName} avatar`"
        class="size-10 rounded-full border border-border-subtle object-cover"
      >
      <div
        v-else
        class="flex size-10 shrink-0 items-center justify-center rounded-full border border-border-subtle bg-bg-surface font-mono text-[11px] font-bold uppercase text-text-display"
      >
        {{ preview.authorInitials }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1.5">
          <p class="truncate text-[13px] font-semibold text-text-display">
            {{ preview.authorName }}
          </p>
          <span class="shrink-0 text-[10px] font-normal text-text-secondary"> • {{ t('composer.previewMeta.connectionDegree') }}</span>
        </div>
        <p class="truncate text-[11px] text-text-secondary">
          {{ preview.authorHandle }}
        </p>
        <p class="mt-0.5 flex items-center gap-1 text-[10px] text-text-secondary">
          <span>{{ t('composer.previewMeta.justNow') }}</span>
          <span>•</span>
          <Globe class="size-2.5" />
        </p>
      </div>
    </div>

    <div class="px-3.5 pb-3.5 text-[13px] leading-relaxed whitespace-pre-wrap break-words text-text-display [overflow-wrap:anywhere]">
      <span v-if="preview.text.trim().length === 0" class="text-text-secondary italic">
        {{ preview.placeholderText }}
      </span>
      <template v-else>
        <p :class="isTruncated ? LINKEDIN_PREVIEW_CLAMP_CLASS : ''" data-testid="linkedin-preview-text">
          {{ truncatedText }}
        </p>
        <span
          v-if="isTruncated"
          class="mt-1 inline-flex text-[12px] font-semibold text-text-secondary"
          data-testid="linkedin-preview-more"
        >
          {{ t('composer.previewMeta.more') }}
        </span>
      </template>
    </div>

    <div
      v-if="preview.media"
      class="flex max-h-[220px] items-center justify-center overflow-hidden border-t border-border-subtle bg-bg-surface"
      data-testid="linkedin-preview-media"
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
        <span class="rounded-full border border-border-subtle px-2 py-1 text-[10px] font-semibold text-text-secondary">
          {{ t('composer.previewMeta.video') }}
        </span>
      </div>
    </div>

    <div class="flex justify-around border-t border-border-subtle px-1 py-1 text-[11px] font-semibold text-text-secondary">
      <button type="button" class="inline-flex items-center gap-1 rounded px-2 py-2 hover:bg-bg-surface">
        <ThumbsUp class="size-3" />
        <span>{{ t('composer.previewMeta.like') }}</span>
      </button>
      <button type="button" class="inline-flex items-center gap-1 rounded px-2 py-2 hover:bg-bg-surface">
        <MessageCircle class="size-3" />
        <span>{{ t('composer.previewMeta.comment') }}</span>
      </button>
      <button type="button" class="inline-flex items-center gap-1 rounded px-2 py-2 hover:bg-bg-surface">
        <Repeat2 class="size-3" />
        <span>{{ t('composer.previewMeta.repost') }}</span>
      </button>
      <button type="button" class="inline-flex items-center gap-1 rounded px-2 py-2 hover:bg-bg-surface">
        <Send class="size-3" />
        <span>{{ t('composer.previewMeta.send') }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.preview-text-clamp {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
</style>
