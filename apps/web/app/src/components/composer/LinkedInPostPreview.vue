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
  <div class="w-full max-w-[360px] bg-[#1d2226] text-white border border-[#2d3135] rounded-xl overflow-hidden shadow-md font-sans text-xs">
    <div class="p-3.5 flex gap-3">
      <img
        v-if="preview.authorAvatarUrl"
        :src="proxyImageUrl(preview.authorAvatarUrl)"
        :alt="`${preview.authorName} avatar`"
        class="size-10 rounded-full object-cover border border-[#404448]"
      />
      <div
        v-else
        class="flex size-10 shrink-0 items-center justify-center rounded-full border border-[#404448] bg-[#111417] font-mono text-[11px] font-bold uppercase text-white"
      >
        {{ preview.authorInitials }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1.5">
          <p class="font-semibold text-white text-[13px] hover:text-[#70b5f9] hover:underline cursor-pointer truncate">
            {{ preview.authorName }}
          </p>
          <span class="text-[10px] text-gray-400 font-normal shrink-0"> • 1st</span>
        </div>
        <p class="text-[11px] text-gray-400 truncate">
          {{ preview.authorHandle }}
        </p>
        <p class="text-[10px] text-gray-400 flex items-center gap-1 mt-0.5">
          <span>Just now</span>
          <span>•</span>
          <Globe class="size-2.5" />
        </p>
      </div>
    </div>

    <div class="px-3.5 pb-3.5 text-white text-[13px] leading-relaxed whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
      <span v-if="preview.text.trim().length === 0" class="text-gray-500 italic">
        {{ preview.placeholderText }}
      </span>
      <template v-else>
        <p :class="isTruncated ? LINKEDIN_PREVIEW_CLAMP_CLASS : ''" data-testid="linkedin-preview-text">
          {{ truncatedText }}
        </p>
        <span
          v-if="isTruncated"
          class="mt-1 inline-flex text-[12px] font-semibold text-gray-300"
          data-testid="linkedin-preview-more"
        >
          ...more
        </span>
      </template>
    </div>

    <div
      v-if="preview.media"
      class="border-t border-[#2d3135] max-h-[220px] overflow-hidden bg-black/30 flex items-center justify-center"
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
          <p class="font-mono text-[10px] uppercase tracking-[0.2em] text-gray-400">
            Media attachment
          </p>
          <p class="mt-1 text-[12px] font-medium text-white">
            {{ preview.media.name ?? 'Video attachment' }}
          </p>
        </div>
        <span class="rounded-full border border-[#404448] px-2 py-1 text-[10px] font-semibold text-gray-300">
          Video
        </span>
      </div>
    </div>

    <div class="border-t border-[#2d3135] py-2 px-1 flex justify-around text-gray-400 font-semibold text-[11px]">
      <span class="flex items-center gap-1.5 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">
        <ThumbsUp class="size-3.5" />
        Like
      </span>
      <span class="flex items-center gap-1.5 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">
        <MessageCircle class="size-3.5" />
        Comment
      </span>
      <span class="flex items-center gap-1.5 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">
        <Repeat2 class="size-3.5" />
        Repost
      </span>
      <span class="flex items-center gap-1.5 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">
        <Send class="size-3.5" />
        Send
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
