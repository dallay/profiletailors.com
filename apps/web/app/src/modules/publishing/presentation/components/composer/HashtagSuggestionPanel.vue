<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Hash, TrendingUp, Bookmark, BookmarkPlus, X, ChevronDown, ChevronUp, Loader2 } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { LINKEDIN_HASHTAG_LIMIT } from '@modules/publishing/presentation/composables/useHashtagSuggestions'
import type { HashtagSuggestion, HashtagSavedSet } from '@modules/publishing/services/hashtag-api'

const props = defineProps<{
  suggestions: HashtagSuggestion[]
  trending: HashtagSuggestion[]
  savedSets: HashtagSavedSet[]
  addedHashtags: Set<string>
  hashtagCount: number
  isAtLimit: boolean
  isApproachingLimit: boolean
  isAnalyzing: boolean
  isSaving: boolean
}>()

const emit = defineEmits<{
  (e: 'add', hashtag: string): void
  (e: 'remove', hashtag: string): void
  (e: 'apply-set', set: HashtagSavedSet): void
  (e: 'save-current-as-set', hashtags: string[]): void
  (e: 'delete-set', setId: string): void
}>()

const { t } = useI18n()

const isSavedSetsExpanded = ref(false)
const isTrendingExpanded = ref(false)
const saveSetName = ref('')
const isSaveFormOpen = ref(false)

const displaySuggestions = computed(() => {
  if (props.suggestions.length > 0) return props.suggestions
  return props.trending.slice(0, 5)
})

const limitLabel = computed(() => {
  const count = props.hashtagCount
  if (count >= LINKEDIN_HASHTAG_LIMIT) return t('composer.hashtags.limitReached', { max: LINKEDIN_HASHTAG_LIMIT })
  if (props.isApproachingLimit) return t('composer.hashtags.limitWarning', { count, max: LINKEDIN_HASHTAG_LIMIT })
  return t('composer.hashtags.count', { count, max: LINKEDIN_HASHTAG_LIMIT })
})

const limitLabelClass = computed(() => {
  if (props.isAtLimit) return 'text-red-500'
  if (props.isApproachingLimit) return 'text-amber-500'
  return 'text-text-secondary'
})

function onAdd(hashtag: string) {
  if (!props.isAtLimit) emit('add', hashtag)
}

function onRemove(hashtag: string) {
  emit('remove', hashtag)
}

function onApplySet(set: HashtagSavedSet) {
  emit('apply-set', set)
}

function onDeleteSet(setId: string) {
  emit('delete-set', setId)
}

function submitSaveSet() {
  if (!saveSetName.value.trim()) return
  emit('save-current-as-set', [...props.addedHashtags])
  saveSetName.value = ''
  isSaveFormOpen.value = false
}
</script>

<template>
  <aside
    aria-label="Hashtag suggestions"
    class="flex flex-col gap-4 p-4 border-l border-border-subtle bg-bg-primary min-h-0 overflow-y-auto w-72 shrink-0"
  >
    <!-- Header -->
    <div class="flex items-center justify-between gap-2">
      <div class="flex items-center gap-1.5 text-text-display">
        <Hash class="size-4 shrink-0" />
        <span class="text-sm font-semibold">{{ t('composer.hashtags.title') }}</span>
      </div>
      <span :class="['text-xs tabular-nums font-medium', limitLabelClass]">
        {{ limitLabel }}
      </span>
    </div>

    <!-- Limit error -->
    <p v-if="isAtLimit" role="alert" class="rounded-md bg-red-500/10 px-3 py-2 text-xs text-red-500">
      {{ t('composer.hashtags.limitError', { max: LINKEDIN_HASHTAG_LIMIT }) }}
    </p>

    <!-- Analyzing indicator -->
    <div v-if="isAnalyzing" class="flex items-center gap-2 text-text-secondary text-xs">
      <Loader2 class="size-3 animate-spin shrink-0" />
      {{ t('composer.hashtags.analyzing') }}
    </div>

    <!-- Suggestions list -->
    <section v-if="!isAnalyzing && displaySuggestions.length > 0" aria-label="Suggestions">
      <p class="mb-2 text-xs font-medium text-text-secondary uppercase tracking-wide">
        {{ suggestions.length > 0 ? t('composer.hashtags.suggestedLabel') : t('composer.hashtags.trendingLabel') }}
      </p>
      <ul class="flex flex-wrap gap-1.5">
        <li v-for="item in displaySuggestions" :key="item.hashtag">
          <button
            type="button"
            :disabled="isAtLimit && !addedHashtags.has(item.hashtag)"
            :aria-pressed="addedHashtags.has(item.hashtag)"
            :title="addedHashtags.has(item.hashtag) ? t('composer.hashtags.removeTag') : t('composer.hashtags.addTag')"
            class="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-border-focus disabled:opacity-40 disabled:cursor-not-allowed"
            :class="addedHashtags.has(item.hashtag)
              ? 'bg-accent-primary/15 border-accent-primary/40 text-accent-primary'
              : 'bg-bg-secondary border-border-subtle text-text-primary hover:border-accent-primary/50'"
            @click="addedHashtags.has(item.hashtag) ? onRemove(item.hashtag) : onAdd(item.hashtag)"
          >
            <TrendingUp v-if="item.popularity === 'trending'" class="size-2.5 shrink-0" />
            {{ item.hashtag }}
          </button>
        </li>
      </ul>
    </section>

    <!-- Added hashtags -->
    <section v-if="addedHashtags.size > 0" aria-label="Added hashtags">
      <p class="mb-2 text-xs font-medium text-text-secondary uppercase tracking-wide">
        {{ t('composer.hashtags.addedLabel') }}
      </p>
      <ul class="flex flex-wrap gap-1.5">
        <li v-for="tag in addedHashtags" :key="tag" class="inline-flex items-center gap-0.5 rounded-full bg-accent-primary/15 border border-accent-primary/40 pl-2 pr-1 py-0.5">
          <span class="text-xs text-accent-primary">{{ tag }}</span>
          <button
            type="button"
            :aria-label="t('composer.hashtags.removeTag')"
            class="size-3.5 flex items-center justify-center rounded-full text-accent-primary/70 hover:text-accent-primary"
            @click="onRemove(tag)"
          >
            <X class="size-2.5" />
          </button>
        </li>
      </ul>

      <!-- Save as set -->
      <div class="mt-3">
        <Button
          v-if="!isSaveFormOpen"
          variant="ghost"
          size="xs"
          class="text-xs gap-1 h-auto px-1 py-0.5 text-text-secondary hover:text-text-primary"
          @click="isSaveFormOpen = true"
        >
          <BookmarkPlus class="size-3" />
          {{ t('composer.hashtags.saveAsSet') }}
        </Button>
        <form v-else class="flex items-center gap-1.5 mt-1" @submit.prevent="submitSaveSet">
          <label class="sr-only" for="hashtag-save-set-name">
            {{ t('composer.hashtags.setNamePlaceholder') }}
          </label>
          <input
            id="hashtag-save-set-name"
            v-model="saveSetName"
            type="text"
            :placeholder="t('composer.hashtags.setNamePlaceholder')"
            maxlength="80"
            class="flex-1 min-w-0 rounded border border-border-subtle bg-bg-secondary px-2 py-1 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-1 focus:ring-border-focus"
          />
          <Button type="submit" size="xs" :disabled="!saveSetName.trim() || isSaving" class="h-auto px-2 py-1 text-xs">
            {{ t('composer.hashtags.saveSetBtn') }}
          </Button>
          <button
            type="button"
            class="text-text-secondary hover:text-text-primary"
            @click="isSaveFormOpen = false; saveSetName = ''"
          >
            <X class="size-3.5" />
          </button>
        </form>
      </div>
    </section>

    <!-- Saved sets -->
    <section v-if="savedSets.length > 0" aria-label="Saved sets">
      <button
        type="button"
        class="flex w-full items-center justify-between text-xs font-medium text-text-secondary uppercase tracking-wide mb-1"
        @click="isSavedSetsExpanded = !isSavedSetsExpanded"
      >
        <span class="flex items-center gap-1">
          <Bookmark class="size-3" />
          {{ t('composer.hashtags.savedSetsLabel') }}
        </span>
        <ChevronDown v-if="!isSavedSetsExpanded" class="size-3" />
        <ChevronUp v-else class="size-3" />
      </button>
      <ul v-if="isSavedSetsExpanded" class="space-y-1.5">
        <li
          v-for="set in savedSets"
          :key="set.id"
          class="flex items-center justify-between gap-2 rounded-md border border-border-subtle bg-bg-secondary px-2.5 py-2"
        >
          <div class="min-w-0">
            <p class="truncate text-xs font-medium text-text-primary">{{ set.name }}</p>
            <p class="truncate text-[10px] text-text-secondary">{{ set.hashtags.join(' ') }}</p>
          </div>
          <div class="flex shrink-0 items-center gap-1">
            <Button
              variant="ghost"
              size="xs"
              :disabled="isAtLimit"
              class="h-auto px-1.5 py-0.5 text-xs"
              @click="onApplySet(set)"
            >
              {{ t('composer.hashtags.applySet') }}
            </Button>
            <button
              type="button"
              :aria-label="t('composer.hashtags.deleteSet')"
              class="text-text-muted hover:text-red-500 transition-colors"
              @click="onDeleteSet(set.id)"
            >
              <X class="size-3.5" />
            </button>
          </div>
        </li>
      </ul>
    </section>

    <!-- Trending section (expandable) -->
    <section v-if="trending.length > 0 && suggestions.length > 0" aria-label="Trending hashtags">
      <button
        type="button"
        class="flex w-full items-center justify-between text-xs font-medium text-text-secondary uppercase tracking-wide mb-1"
        @click="isTrendingExpanded = !isTrendingExpanded"
      >
        <span class="flex items-center gap-1">
          <TrendingUp class="size-3" />
          {{ t('composer.hashtags.trendingLabel') }}
        </span>
        <ChevronDown v-if="!isTrendingExpanded" class="size-3" />
        <ChevronUp v-else class="size-3" />
      </button>
      <ul v-if="isTrendingExpanded" class="flex flex-wrap gap-1.5">
        <li v-for="item in trending" :key="item.hashtag">
          <button
            type="button"
            :disabled="isAtLimit && !addedHashtags.has(item.hashtag)"
            :aria-pressed="addedHashtags.has(item.hashtag)"
            :title="addedHashtags.has(item.hashtag) ? t('composer.hashtags.removeTag') : t('composer.hashtags.addTag')"
            class="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            :class="addedHashtags.has(item.hashtag)
              ? 'bg-accent-primary/15 border-accent-primary/40 text-accent-primary'
              : 'bg-bg-secondary border-border-subtle text-text-primary hover:border-accent-primary/50'"
            @click="addedHashtags.has(item.hashtag) ? onRemove(item.hashtag) : onAdd(item.hashtag)"
          >
            <TrendingUp class="size-2.5 shrink-0" />
            {{ item.hashtag }}
          </button>
        </li>
      </ul>
    </section>

    <!-- Empty state -->
    <p v-if="!isAnalyzing && displaySuggestions.length === 0 && addedHashtags.size === 0" class="text-xs text-text-muted text-center py-4">
      {{ t('composer.hashtags.emptyHint') }}
    </p>
  </aside>
</template>
