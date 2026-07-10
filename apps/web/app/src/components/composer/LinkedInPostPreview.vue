<script setup lang="ts">
import { computed } from 'vue'
import { Globe, MessageCircle, Repeat2, Send, ThumbsUp } from '@lucide/vue'
import { proxyImageUrl } from '@/lib/auth-api'
import type { LinkedInPreviewModel } from './post-preview.types'

const props = defineProps<{
  preview: LinkedInPreviewModel
}>()

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
  <div class="w-full max-w-[360px] bg-white text-[#1a1a1a] border border-[#e9e5df] rounded-xl overflow-hidden shadow-md font-sans text-xs dark:bg-[#1d2226] dark:text-white dark:border-[#2d3135]">
    <div class="p-3.5 flex gap-3">
      <img
        v-if="preview.authorAvatarUrl"
        :src="proxyImageUrl(preview.authorAvatarUrl)"
        :alt="`${preview.authorName} avatar`"
        class="size-10 rounded-full object-cover border border-[#e9e5df] dark:border-[#404448]"
      />
      <div
        v-else
        class="flex size-10 shrink-0 items-center justify-center rounded-full border border-[#e9e5df] bg-[#f4f2ee] font-mono text-[11px] font-bold uppercase text-[#1a1a1a] dark:border-[#404448] dark:bg-[#111417] dark:text-white"
      >
        {{ preview.authorInitials }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1.5">
          <p class="font-semibold text-[#1a1a1a] text-[13px] hover:text-[#0a66c2] hover:underline cursor-pointer truncate dark:text-white dark:hover:text-[#70b5f9]">
            {{ preview.authorName }}
          </p>
          <span class="text-[10px] text-[#666] font-normal shrink-0 dark:text-gray-400"> • 1st</span>
        </div>
        <p class="text-[11px] text-[#666] truncate dark:text-gray-400">
          {{ preview.authorHandle }}
        </p>
        <p class="text-[10px] text-[#666] flex items-center gap-1 mt-0.5 dark:text-gray-400">
          <span>Just now</span>
          <span>•</span>
          <Globe class="size-2.5" />
        </p>
      </div>
    </div>

    <div class="px-3.5 pb-3.5 text-[#1a1a1a] text-[13px] leading-relaxed whitespace-pre-wrap break-words [overflow-wrap:anywhere] dark:text-white">
      <span v-if="preview.text.trim().length === 0" class="text-[#666] italic dark:text-gray-500">
        {{ preview.placeholderText }}
      </span>
      <template v-else>
        <p :class="isTruncated ? LINKEDIN_PREVIEW_CLAMP_CLASS : ''" data-testid="linkedin-preview-text">
          {{ truncatedText }}
        </p>
        <span
          v-if="isTruncated"
          class="mt-1 inline-flex text-[12px] font-semibold text-[#666] dark:text-gray-300"
          data-testid="linkedin-preview-more"
        >
          ...more
        </span>
      </template>
    </div>

    <div
      v-if="preview.media"
      class="border-t border-[#e9e5df] max-h-[220px] overflow-hidden bg-[#f4f2ee] flex items-center justify-center dark:border-[#2d3135] dark:bg-black/30"
      data-testid="linkedin-preview-media"
    >
      <img
        v-if="preview.media.kind === 'image' && preview.media.url"
        :src="preview.media.url"
        :alt="preview.media.alt"
        class="w-full h-auto max-h-[220px] object-contain"
      />
      <div
        v-else
        class="flex w-full items-center justify-between gap-3 px-4 py-4 text-left"
      >
        <div class="min-w-0 flex-1">
          <p class="font-mono text-[10px] uppercase tracking-[0.2em] text-[#666] dark:text-gray-400">
            Media attachment
          </p>
          <p class="mt-1 text-[12px] font-medium text-[#1a1a1a] dark:text-white">
            {{ preview.media.name ?? 'Video attachment' }}
          </p>
        </div>
        <span class="rounded-full border border-[#e9e5df] px-2 py-1 text-[10px] font-semibold text-[#666] dark:border-[#404448] dark:text-gray-300">
          Video
        </span>
      </div>
    </div>

    <div class="border-t border-[#e9e5df] py-1 px-1 flex justify-around text-[#666] font-semibold text-[11px] dark:border-[#2d3135] dark:text-gray-400">
      <span class="inline-flex items-center gap-1 px-2 py-2 rounded hover:bg-[#f4f2ee] cursor-pointer dark:hover:bg-white/5">
        <ThumbsUp class="size-3" />
        <span>Like</span>
      </span>
      <span class="inline-flex items-center gap-1 px-2 py-2 rounded hover:bg-[#f4f2ee] cursor-pointer dark:hover:bg-white/5">
        <MessageCircle class="size-3" />
        <span>Comment</span>
      </span>
      <span class="inline-flex items-center gap-1 px-2 py-2 rounded hover:bg-[#f4f2ee] cursor-pointer dark:hover:bg-white/5">
        <Repeat2 class="size-3" />
        <span>Repost</span>
      </span>
      <span class="inline-flex items-center gap-1 px-2 py-2 rounded hover:bg-[#f4f2ee] cursor-pointer dark:hover:bg-white/5">
        <Send class="size-3" />
        <span>Send</span>
      </span>
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
