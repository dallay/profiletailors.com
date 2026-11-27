<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpRight, ChevronLeft, ChevronRight, X } from '@lucide/vue'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@profiletailors/vue-ui/shell/button'
import type { ChangelogEntry, ChangelogLocale } from '@profiletailors/shared-web'
import {
  formatShortChangelogDate,
  type ChangelogStatus,
} from '../application/useChangelog'

const props = defineProps<{
  open: boolean
  status: ChangelogStatus
  entries: readonly ChangelogEntry[]
  activeEntry: ChangelogEntry | null
  activeIndex: number
  hasPrevious: boolean
  hasNext: boolean
  locale: ChangelogLocale
  fullChangelogUrl: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  previous: []
  next: []
  retry: []
  seen: []
}>()

const { t } = useI18n()

const headerDate = computed(() => {
  if (!props.activeEntry) return ''
  return formatShortChangelogDate(props.activeEntry.date, props.locale)
})

const headerExternalUrl = computed(() => props.activeEntry?.url ?? props.fullChangelogUrl)

watch(
  () => [props.open, props.entries.length] as const,
  ([isOpen, count]) => {
    if (isOpen && count > 0) {
      emit('seen')
    }
  },
  { immediate: true },
)
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent
      :show-close-button="false"
      data-testid="changelog-modal"
      class="flex max-h-[85vh] flex-col gap-0 overflow-hidden rounded-2xl border border-border-visible bg-bg-surface p-0 text-text-body sm:max-w-2xl"
    >
      <div
        class="grid grid-cols-[1fr_auto_1fr] items-center border-b border-border-subtle px-5 py-3.5"
      >
        <div class="flex items-center gap-2">
          <time
            v-if="activeEntry"
            :datetime="activeEntry.date"
            data-testid="changelog-modal-date"
            class="font-mono text-xs text-text-secondary"
          >
            {{ headerDate }}
          </time>
          <span
            v-if="activeEntry?.version"
            class="hidden font-mono text-[11px] text-text-muted sm:inline"
          >
            {{ activeEntry.version }}
          </span>
        </div>

        <a
          :href="headerExternalUrl"
          target="_blank"
          rel="noopener noreferrer"
          data-testid="changelog-external-link"
          :aria-label="t('changelog.openFullAria')"
          class="inline-flex min-h-[36px] items-center gap-1 rounded-md px-2 text-sm font-medium text-text-display transition-colors hover:text-text-secondary"
        >
          <span>{{ t('changelog.openFull') }}</span>
          <ArrowUpRight class="size-3.5 text-text-secondary" aria-hidden="true" />
        </a>

        <div class="flex justify-end">
          <DialogClose as-child>
            <Button
              variant="ghost"
              size="icon-sm"
              data-testid="changelog-close-button"
              :aria-label="t('changelog.close')"
              class="text-text-secondary hover:text-text-display"
            >
              <X class="size-4" aria-hidden="true" />
            </Button>
          </DialogClose>
        </div>
      </div>

      <div class="flex-1 overflow-y-auto px-6 py-6 sm:px-8">
        <div
          v-if="status === 'loading'"
          role="status"
          aria-live="polite"
          class="space-y-4"
        >
          <span class="sr-only">{{ t('changelog.loading') }}</span>
          <div class="h-7 w-2/3 animate-pulse rounded bg-bg-primary" />
          <div class="aspect-video w-full animate-pulse rounded-xl border border-border-subtle bg-bg-primary" />
          <div class="h-4 w-full animate-pulse rounded bg-bg-primary" />
          <div class="h-4 w-5/6 animate-pulse rounded bg-bg-primary" />
        </div>

        <div
          v-else-if="status === 'error'"
          role="alert"
          data-testid="changelog-error-state"
          class="flex flex-col items-start gap-3 py-8"
        >
          <DialogTitle class="text-lg font-medium text-text-display">
            {{ t('changelog.errorTitle') }}
          </DialogTitle>
          <DialogDescription class="text-sm text-text-secondary">
            {{ t('changelog.errorDescription') }}
          </DialogDescription>
          <div class="mt-2 flex flex-wrap items-center gap-3">
            <Button size="sm" variant="outline" @click="emit('retry')">
              {{ t('changelog.retry') }}
            </Button>
            <a
              :href="fullChangelogUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1 font-mono text-xs uppercase tracking-wider text-text-display underline underline-offset-4"
            >
              <span>{{ t('changelog.openFull') }}</span>
              <ArrowUpRight class="size-3.5" aria-hidden="true" />
            </a>
          </div>
        </div>

        <article v-else-if="activeEntry" class="space-y-6">
          <header class="space-y-2.5">
            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="category in activeEntry.categories"
                :key="category"
                class="inline-flex items-center rounded-full border border-border-visible px-2.5 py-0.5 font-mono text-[11px] uppercase tracking-[0.06em] text-text-secondary"
              >
                {{ t(`changelog.categories.${category}`) }}
              </span>
            </div>

            <DialogTitle class="text-2xl font-medium tracking-tight text-text-display">
              {{ activeEntry.title }}
            </DialogTitle>
            <DialogDescription class="text-sm leading-relaxed text-text-secondary">
              {{ activeEntry.summary }}
            </DialogDescription>
          </header>

          <figure
            v-if="activeEntry.image"
            class="overflow-hidden rounded-xl border border-border-subtle bg-bg-primary"
          >
            <img
              :src="activeEntry.image"
              :alt="activeEntry.imageAlt ?? activeEntry.title"
              width="960"
              height="540"
              class="block h-auto w-full"
            />
          </figure>

          <section
            v-for="section in activeEntry.sections"
            :key="section.heading"
            class="space-y-3"
          >
            <h3 class="text-base font-medium text-text-display">
              {{ section.heading }}
            </h3>

            <p
              v-for="(paragraph, idx) in section.paragraphs"
              :key="idx"
              class="text-sm leading-relaxed text-text-body"
            >
              {{ paragraph }}
            </p>

            <ul
              v-if="section.bullets && section.bullets.length > 0"
              class="list-disc space-y-1.5 pl-5 text-sm leading-relaxed text-text-secondary"
            >
              <li v-for="(bullet, idx) in section.bullets" :key="idx">
                {{ bullet }}
              </li>
            </ul>

            <figure
              v-if="section.image"
              class="overflow-hidden rounded-xl border border-border-subtle bg-bg-primary"
            >
              <img
                :src="section.image"
                :alt="section.imageAlt ?? section.heading"
                class="block h-auto w-full"
              />
            </figure>

            <a
              v-if="section.link"
              :href="section.link.href"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1 text-sm text-text-display underline underline-offset-4 hover:text-text-secondary"
            >
              <span>{{ section.link.label }}</span>
              <ArrowUpRight class="size-3.5" aria-hidden="true" />
            </a>
          </section>
        </article>

        <div v-else class="py-8">
          <DialogTitle class="text-base font-medium text-text-display">
            {{ t('changelog.title') }}
          </DialogTitle>
          <DialogDescription class="mt-1 text-sm text-text-secondary">
            {{ t('changelog.empty') }}
          </DialogDescription>
        </div>
      </div>

      <div
        v-if="entries.length > 0"
        class="flex items-center justify-between border-t border-border-subtle px-5 py-3"
      >
        <div class="flex items-center gap-1.5">
          <Button
            variant="outline"
            size="sm"
            :disabled="!hasNext"
            data-testid="changelog-next-button"
            @click="emit('next')"
          >
            <ChevronLeft class="size-3.5" aria-hidden="true" />
            <span>{{ t('changelog.next') }}</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            :disabled="!hasPrevious"
            data-testid="changelog-previous-button"
            @click="emit('previous')"
          >
            <span>{{ t('changelog.previous') }}</span>
            <ChevronRight class="size-3.5" aria-hidden="true" />
          </Button>
        </div>

        <span class="font-mono text-xs text-text-secondary">
          {{ activeIndex + 1 }} / {{ entries.length }}
        </span>
      </div>
    </DialogContent>
  </Dialog>
</template>
