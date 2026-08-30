<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { ChevronDown, Link as LinkIcon, Tag } from '@lucide/vue'
import { useIdeasStore } from '@modules/ideas/infrastructure/ideas.store'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { buildPublishingPrefill, useIdeaComposer } from '@modules/ideas/application/useIdeaComposer'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import MarkdownToolbar from '@modules/publishing/presentation/components/composer/MarkdownToolbar.vue'
import ComposerSchedulePanel from '@modules/publishing/presentation/components/composer/ComposerSchedulePanel.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    idea?: Idea | null
    columns: IdeaColumn[]
    initialColumnId?: string | null
  }>(),
  {
    idea: null,
    initialColumnId: null,
  },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved', idea: Idea): void
  (e: 'deleted', id: string): void
  (e: 'close'): void
  (e: 'handoff', payload: { ideaId: string; prefill: string }): void
}>()

const { t } = useI18n()
const ideasStore = useIdeasStore()
const publishingStore = usePublishingStore()
const hasNoChannels = computed(() => {
  const raw = (publishingStore.hasNoChannels as unknown as { value?: boolean } | boolean)
  if (typeof raw === 'boolean') return raw
  return !!(raw as { value: boolean })?.value
})

const columnsRef = computed(() => props.columns)
const ideaRef = computed(() => props.idea ?? null)
const initialColumnIdRef = computed(() => props.initialColumnId ?? props.idea?.columnId ?? null)
const composer = useIdeaComposer({
  idea: ideaRef as never,
  columns: columnsRef,
  store: {
    createIdea: ideasStore.createIdea,
    updateIdea: ideasStore.updateIdea,
    saving: ideasStore.saving as never,
  },
  initialColumnId: initialColumnIdRef as never,
})

const tagInput = ref('')
const linkLabel = ref('')
const linkUrl = ref('')
const isDeleteConfirmOpen = ref(false)
const isDirtyGuardOpen = ref(false)
const isDetailsOpen = ref(false)

watch(
  () => props.idea,
  () => {
    isDeleteConfirmOpen.value = false
    isDirtyGuardOpen.value = false
  },
)

watch(
  () => props.open,
  (open, wasOpen) => {
    if (!open) {
      isDeleteConfirmOpen.value = false
      isDirtyGuardOpen.value = false
      isDetailsOpen.value = false
    }
    if (open && !wasOpen) {
      composer.reset()
      tagInput.value = ''
      linkLabel.value = ''
      linkUrl.value = ''
    }
  },
)

const isEditMode = computed(() => !!props.idea)

function handleAddTag(): void {
  const raw = tagInput.value.trim()
  if (!raw) return
  composer.addTag(raw)
  tagInput.value = ''
}

function handleAddLink(): void {
  const ok = composer.addLink({ label: linkLabel.value, url: linkUrl.value })
  if (ok) {
    linkLabel.value = ''
    linkUrl.value = ''
  }
}

async function handleSave(): Promise<void> {
  if (composer.isSaving.value) return
  const result = await composer.save()
  if (!result) {
    if (composer.titleError.value) toast.error(t('ideas.composer.validation.titleRequired'))
    return
  }
  toast.success(t(isEditMode.value ? 'ideas.toasts.updated' : 'ideas.toasts.created'))
  emit('saved', result)
  emit('update:open', false)
  emit('close')
}

function handleRequestClose(): void {
  if (composer.isDirty.value) {
    isDirtyGuardOpen.value = true
    return
  }
  emit('update:open', false)
  emit('close')
}

function handleDirtyConfirm(): void {
  isDirtyGuardOpen.value = false
  emit('update:open', false)
  emit('close')
}

function handleDirtyCancel(): void {
  isDirtyGuardOpen.value = false
}

function handleEscape(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  if (composer.isDirty.value) {
    event.preventDefault()
    isDirtyGuardOpen.value = true
    return
  }
  emit('update:open', false)
  emit('close')
}

async function handleDeleteConfirm(): Promise<void> {
  if (!props.idea) return
  try {
    await ideasStore.deleteIdea(props.idea.id)
    toast.success(t('ideas.toasts.deleted'))
    isDeleteConfirmOpen.value = false
    emit('deleted', props.idea.id)
    emit('update:open', false)
    emit('close')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('ideas.toasts.genericError'))
  }
}

async function handleCreatePost(): Promise<void> {
  if (hasNoChannels.value) return
  if (composer.titleError.value) {
    toast.error(t('ideas.composer.validation.titleRequired'))
    return
  }
  if (composer.isSaving.value) return
  let ideaForPrefill: Idea | null = props.idea ?? null
  if (!ideaForPrefill) {
    const created = await composer.save()
    if (!created) return
    ideaForPrefill = created
    emit('saved', created)
  } else if (composer.isDirty.value) {
    const updated = await composer.save()
    if (updated) {
      ideaForPrefill = updated
      emit('saved', updated)
    }
  }
  if (!ideaForPrefill) return
  const prefill = buildPublishingPrefill(ideaForPrefill)
  emit('handoff', { ideaId: ideaForPrefill.id, prefill })
  emit('update:open', false)
  emit('close')
}
</script>

<template>
  <Dialog :open="open" @update:open="(v: boolean) => (v ? null : handleRequestClose())">
    <DialogContent
      class="w-[min(100%-1rem,640px)] max-h-[calc(100vh-2rem)] gap-0 overflow-hidden rounded-2xl border border-border-visible bg-bg-surface p-0 text-text-body"
      data-testid="idea-composer-modal"
      @keydown="handleEscape"
      @escape-key-down.prevent="handleRequestClose"
    >
      <DialogHeader class="border-b border-border-subtle px-6 pb-4 pt-6 text-left">
        <DialogTitle class="text-lg font-medium text-text-display">
          {{ isEditMode ? t('ideas.composer.editTitle') : t('ideas.composer.createTitle') }}
        </DialogTitle>
        <DialogDescription class="mt-1 text-sm text-text-secondary">
          {{ isEditMode ? t('ideas.composer.editDescription') : t('ideas.composer.createDescription') }}
        </DialogDescription>
      </DialogHeader>

      <div class="max-h-[calc(100vh-11rem)] overflow-y-auto px-6 py-5">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <label class="sr-only" for="idea-composer-column">{{ t('ideas.fields.column') }}</label>
            <Select v-model="composer.columnId.value">
              <SelectTrigger
                id="idea-composer-column"
                data-testid="composer-column-select"
                class="h-9 rounded-lg border-border-visible bg-bg-primary px-3 text-xs"
                :aria-label="t('ideas.fields.column')"
              >
                <SelectValue :placeholder="t('ideas.fields.columnPlaceholder')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="col in columns" :key="col.id" :value="col.id">{{ col.name }}</SelectItem>
              </SelectContent>
            </Select>
            <select
              id="idea-composer-column-native"
              data-testid="composer-column-select-native"
              v-model="composer.columnId.value"
              class="hidden"
              :aria-label="t('ideas.fields.column')"
            >
              <option v-for="col in columns" :key="`native-${col.id}`" :value="col.id">{{ col.name }}</option>
            </select>
          </div>
          <button
            type="button"
            class="inline-flex min-h-9 items-center gap-2 rounded-lg border border-border-visible bg-bg-primary px-3 text-xs text-text-secondary transition hover:border-text-secondary hover:text-text-body"
            data-testid="composer-details-toggle"
            :aria-expanded="isDetailsOpen"
            @click="isDetailsOpen = !isDetailsOpen"
          >
            <Tag class="size-3.5" />
            {{ t('ideas.fields.tags') }}
            <span v-if="composer.tags.value.length" class="font-mono text-[10px]">{{ composer.tags.value.length }}</span>
            <ChevronDown class="size-3.5 transition" :class="isDetailsOpen ? 'rotate-180' : ''" />
          </button>
        </div>

        <div class="mt-5 space-y-2">
          <label class="sr-only" for="idea-composer-title">{{ t('ideas.fields.title') }}</label>
          <Input
            id="idea-composer-title"
            data-testid="composer-title-input"
            v-model="composer.title.value"
            :placeholder="t('ideas.composer.fields.titlePlaceholder')"
            class="h-auto border-0 bg-transparent px-0 text-xl font-medium text-text-display shadow-none placeholder:text-text-secondary/70 focus-visible:ring-0"
          />
          <p
            v-if="composer.titleError.value"
            class="text-xs text-red-500"
            data-testid="composer-title-error"
          >
            {{ t('ideas.composer.validation.titleRequired') }}
          </p>
        </div>

        <div class="mt-4 overflow-hidden rounded-xl border border-border-subtle bg-bg-primary/30">
          <label for="idea-composer-notes" class="sr-only">{{ t('ideas.fields.notes') }}</label>
          <Textarea
            id="idea-composer-notes"
            data-testid="composer-notes"
            v-model="composer.notes.value"
            :placeholder="t('ideas.composer.fields.notesPlaceholder')"
            class="min-h-[260px] resize-none border-0 bg-transparent px-4 py-4 text-sm leading-6 shadow-none focus-visible:ring-0"
            @keydown="composer.markdownEditor.handleKeyDown"
          />
          <div class="border-t border-border-subtle px-3 py-2">
            <MarkdownToolbar
              :disabled="composer.isSaving.value"
              data-testid="markdown-toolbar"
              @bold="composer.markdownEditor.applyBold()"
              @italic="composer.markdownEditor.applyItalic()"
              @strikethrough="composer.markdownEditor.applyStrikethrough()"
              @heading="composer.markdownEditor.applyHeading()"
              @unordered-list="composer.markdownEditor.applyUnorderedList()"
              @ordered-list="composer.markdownEditor.applyOrderedList()"
              @blockquote="composer.markdownEditor.applyBlockquote()"
              @link="composer.markdownEditor.applyLink()"
              @code="composer.markdownEditor.applyInlineCode()"
            />
          </div>
        </div>

        <div v-show="isDetailsOpen" class="mt-4 space-y-5 border-t border-border-subtle pt-4">
          <div class="space-y-2">
            <label class="label-mono text-text-secondary" for="idea-composer-tag-input">{{ t('ideas.fields.tags') }}</label>
          <div class="flex gap-2">
            <Input
              id="idea-composer-tag-input"
              data-testid="composer-tag-input"
              v-model="tagInput"
              :placeholder="t('ideas.composer.fields.tagsPlaceholder')"
              @keydown.enter.prevent="handleAddTag"
            />
            <Button type="button" variant="outline" data-testid="composer-tag-add" @click="handleAddTag">{{ t('common.add') }}</Button>
            </div>
            <div class="flex flex-wrap gap-1.5" data-testid="composer-tags">
            <span
              v-for="tag in composer.tags.value"
              :key="tag"
              :data-testid="`tag-chip-${tag}`"
              class="inline-flex items-center gap-1 rounded-full bg-bg-surface px-2 py-1 text-xs"
            >
              #{{ tag }}
              <button
                type="button"
                :data-testid="`tag-remove-${tag}`"
                class="ml-1 text-text-secondary hover:text-text-display"
                @click="composer.removeTag(tag)"
              >
                ×
              </button>
            </span>
            </div>
          </div>

          <div class="space-y-2">
            <label class="label-mono inline-flex items-center gap-2 text-text-secondary" for="idea-composer-link-label">
              <LinkIcon class="size-3.5" />
              {{ t('ideas.fields.links') }}
            </label>
            <div class="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
            <Input
              id="idea-composer-link-label"
              data-testid="composer-link-label"
              v-model="linkLabel"
              :placeholder="t('ideas.composer.fields.linkLabelPlaceholder')"
            />
            <label for="idea-composer-link-url" class="sr-only">{{ t('ideas.composer.fields.linkUrlLabel') }}</label>
            <Input
              id="idea-composer-link-url"
              data-testid="composer-link-url"
              v-model="linkUrl"
              placeholder="https://example.com"
              @keydown.enter.prevent="handleAddLink"
            />
            <Button type="button" variant="outline" data-testid="composer-link-add" @click="handleAddLink">{{ t('common.add') }}</Button>
            </div>
            <p v-if="composer.linkError.value" class="text-xs text-red-500" data-testid="composer-link-error">
              {{ composer.linkError.value }}
            </p>
            <div class="space-y-1" data-testid="composer-links">
            <div
              v-for="link in composer.links.value"
              :key="link.url"
              :data-testid="`link-chip-${link.url}`"
              class="flex items-center justify-between rounded-lg border border-border-subtle bg-bg-surface px-3 py-2 text-xs"
            >
              <span class="truncate">{{ link.label ? `${link.label} — ${link.url}` : link.url }}</span>
              <button
                type="button"
                :data-testid="`link-remove-${link.url}`"
                class="ml-2 text-text-secondary hover:text-text-display"
                @click="composer.removeLink(link.url)"
              >
                ×
              </button>
            </div>
            </div>
          </div>

          <ComposerSchedulePanel
            :schedule-mode="composer.scheduling.scheduleMode.value"
            :selected-calendar-date="composer.scheduling.selectedCalendarDate.value"
            :schedule-time="composer.scheduling.scheduleTime.value"
            :is-date-picker-open="composer.scheduling.isDatePickerOpen.value"
            :today-date-value="(composer.scheduling.todayDateValue.value as unknown as import('@internationalized/date').CalendarDate)"
            :min-time-for-date="composer.scheduling.minTimeForDate.value"
            :selected-date-label="composer.scheduling.selectedDateLabel.value"
            :schedule-helper-text="composer.scheduling.scheduleHelperText.value"
            data-testid="schedule-panel"
            @update:schedule-mode="(v) => composer.scheduling.setScheduleMode(v)"
            @update:selected-calendar-date="(v) => composer.scheduling.setScheduleDate(v)"
            @update:schedule-time="(v) => composer.scheduling.setScheduleTime(v)"
            @update:is-date-picker-open="(v) => (composer.scheduling.isDatePickerOpen.value = v)"
          />
        </div>
      </div>

      <DialogFooter class="flex flex-wrap items-center justify-between gap-3 border-t border-border-subtle bg-bg-primary/30 px-6 py-4">
        <div>
          <Button
            v-if="isEditMode"
            type="button"
            variant="destructive"
            data-testid="composer-delete"
            :disabled="composer.isSaving.value"
            @click="isDeleteConfirmOpen = true"
          >
            {{ t('ideas.actions.delete') }}
          </Button>
        </div>
        <div class="flex flex-wrap gap-2">
          <Button type="button" variant="outline" data-testid="composer-cancel" @click="handleRequestClose">{{ t('common.cancel') }}</Button>
          <Button
            type="button"
            data-testid="composer-create-post"
            :disabled="hasNoChannels || !!composer.titleError.value || composer.isSaving.value"
            @click="handleCreatePost"
          >
            {{ t('ideas.composer.createPost') }}
          </Button>
          <Button
            type="button"
            data-testid="composer-save"
            :disabled="!!composer.titleError.value || composer.isSaving.value"
            @click="handleSave"
          >
            {{ composer.isSaving.value ? t('common.saving') : t('ideas.actions.save') }}
          </Button>
        </div>
      </DialogFooter>
      <p
        v-if="hasNoChannels"
        data-testid="composer-no-channels-cta"
        class="flex flex-wrap gap-1 py-2 text-xs text-text-secondary"
      >
        {{ t('ideas.composer.noChannelsCta') }}
      </p>

      <Dialog
        v-if="isDeleteConfirmOpen"
        :open="isDeleteConfirmOpen"
        @update:open="(v: boolean) => (isDeleteConfirmOpen = v)"
      >
        <DialogContent data-testid="composer-delete-confirm">
          <DialogHeader>
            <DialogTitle>{{ t('ideas.composer.deleteConfirm.title') }}</DialogTitle>
            <DialogDescription>{{ t('ideas.composer.deleteConfirm.description') }}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" data-testid="composer-delete-cancel" @click="isDeleteConfirmOpen = false">{{ t('common.cancel') }}</Button>
            <Button type="button" variant="destructive" data-testid="composer-delete-confirm-btn" @click="handleDeleteConfirm">{{ t('ideas.actions.delete') }}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        v-if="isDirtyGuardOpen"
        :open="isDirtyGuardOpen"
        @update:open="(v: boolean) => (isDirtyGuardOpen = v)"
      >
        <DialogContent data-testid="composer-dirty-guard">
          <DialogHeader>
            <DialogTitle>{{ t('ideas.composer.dirtyGuard.title') }}</DialogTitle>
            <DialogDescription>{{ t('ideas.composer.dirtyGuard.description') }}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" data-testid="composer-dirty-cancel" @click="handleDirtyCancel">{{ t('common.cancel') }}</Button>
            <Button type="button" variant="destructive" data-testid="composer-dirty-confirm" @click="handleDirtyConfirm">{{ t('ideas.composer.dirtyGuard.confirm') }}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </DialogContent>
  </Dialog>
</template>
