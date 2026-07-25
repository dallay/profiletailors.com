<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ImageIcon, ChevronDown, Hash, Smile, Sparkles, X } from '@lucide/vue'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Progress } from '@/components/ui/progress'
import Spinner from '@/components/ui/spinner/Spinner.vue'
import { useComposerAttachmentDisplay } from './useComposerAttachmentDisplay'
import type { ComposerInlineAttachment } from './composer.types'

const props = defineProps<{
  attachments: ComposerInlineAttachment[]
  isDropzoneActive: boolean
  isLocalUploadInFlight: boolean
  normalizedUploadProgress: number | null
  currentUploadStateLabel: string | null
  hasUnsplash: boolean
  charsRemaining: number
  charLimit: number
  isTextTooLong: boolean
}>()

const emit = defineEmits<{
  (e: 'open-upload-picker'): void
  (e: 'open-media-library'): void
  (e: 'open-unsplash-library'): void
  (e: 'emoji-click'): void
  (e: 'hashtag-click'): void
  (e: 'ai-assist-click'): void
  (e: 'remove-draft-attachment', assetId: string): void
  (e: 'remove-local-upload'): void
  (e: 'update:isDropzoneActive', active: boolean): void
  (e: 'dropzone-files', event: DragEvent): void
}>()

const { t } = useI18n()

const isMediaSourcesOpen = ref(false)
const { visibleAttachments, hiddenCount } = useComposerAttachmentDisplay(props.attachments)

function handleOpenMediaLibrary() {
  isMediaSourcesOpen.value = false
  emit('open-media-library')
}

function handleOpenUnsplashLibrary() {
  isMediaSourcesOpen.value = false
  emit('open-unsplash-library')
}

function handleDropzoneDragOver(event: DragEvent) {
  event.preventDefault()
  emit('update:isDropzoneActive', true)
}

function handleDropzoneDragLeave(event: DragEvent) {
  event.preventDefault()
  emit('update:isDropzoneActive', false)
}

function handleDropzoneDrop(event: DragEvent) {
  event.preventDefault()
  emit('update:isDropzoneActive', false)
  emit('dropzone-files', event)
}
</script>

<template>
  <div class="border-t border-border-subtle/70 px-4 py-4">
    <!-- Attachment thumbnails row -->
    <div class="flex flex-wrap items-center gap-3">
      <div
        v-for="asset in visibleAttachments"
        :key="asset.key"
        :title="asset.name"
        class="group relative h-[118px] w-[118px] overflow-hidden rounded-[18px] border border-border-visible bg-bg-primary/50"
        :data-testid="asset.kind === 'draft' ? `inline-attachment-${asset.assetId}` : 'inline-local-upload'"
      >
        <img
          v-if="asset.previewUrl"
          :src="asset.previewUrl"
          alt="Selected media preview"
          class="h-full w-full object-cover"
          data-testid="attachment-preview-image"
        >
        <div
          v-else
          class="flex h-full w-full items-center justify-center bg-bg-primary/40 text-text-secondary"
        >
          <ImageIcon class="size-6" />
        </div>

        <div
          v-if="asset.kind === 'local-upload' ? isLocalUploadInFlight || asset.isUploading : asset.isUploading"
          class="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/55 px-3 text-center backdrop-blur-sm"
          data-testid="inline-upload-overlay"
        >
          <Spinner class="size-5 text-[var(--upload-accent)]" />
          <p class="text-xs font-medium leading-tight text-white">
            {{
              asset.kind === 'local-upload'
                ? (currentUploadStateLabel ?? asset.uploadStateLabel ?? t('composer.media.uploadingProgress', { progress: Math.round(normalizedUploadProgress ?? 0) }))
                : asset.uploadStateLabel
            }}
          </p>
          <Progress
            :model-value="asset.kind === 'local-upload' ? (normalizedUploadProgress ?? 0) : (asset.uploadProgress ?? 0)"
            class="h-1 w-full bg-white/15 [&_[data-slot=progress-indicator]]:bg-[var(--upload-accent)]"
          />
          <p class="text-[10px] leading-tight text-white/70">
            {{ t('composer.media.keepEditingWhileUploading') }}
          </p>
        </div>

        <button
          type="button"
          class="absolute right-2 top-2 flex size-7 items-center justify-center rounded-full bg-black/70 text-white transition hover:bg-black/85"
          :data-testid="asset.kind === 'draft' ? `attachment-remove-${asset.assetId}` : 'attachment-remove-local-upload'"
          :aria-label="t('composer.media.removeAttachment', { name: asset.name })"
          @click="asset.kind === 'draft' ? emit('remove-draft-attachment', asset.assetId) : emit('remove-local-upload')"
        >
          <X class="size-3.5" />
        </button>
      </div>

      <!-- Overflow badge -->
      <div
        v-if="hiddenCount > 0"
        class="flex h-[118px] w-[118px] items-center justify-center rounded-[18px] border border-dashed border-border-visible bg-bg-primary/30 font-mono text-xs tracking-[0.2em] text-text-secondary"
        data-testid="inline-attachment-overflow"
      >
        +{{ hiddenCount }}
      </div>

      <!-- Dropzone button -->
      <button
        type="button"
        class="flex h-[118px] w-[118px] cursor-pointer flex-col items-center justify-center rounded-[18px] border border-dashed px-4 text-center transition"
        :class="isDropzoneActive
          ? 'border-[var(--upload-accent)] bg-[var(--upload-accent)]/10'
          : 'border-border-visible bg-bg-primary/30 hover:border-text-display/40'"
        data-testid="composer-inline-dropzone"
        @click="emit('open-upload-picker')"
        @dragover="handleDropzoneDragOver"
        @dragleave="handleDropzoneDragLeave"
        @drop="handleDropzoneDrop"
      >
        <ImageIcon class="mb-3 size-6 text-text-secondary" />
        <p class="text-[12px] leading-5 text-text-secondary">
          {{ t('composer.media.dropzoneTitle') }}
          <span class="block font-medium text-[var(--upload-accent)]">{{ t('composer.media.dropzoneBody') }}</span>
        </p>
      </button>
    </div>

    <!-- Toolbar row -->
    <div class="mt-4 flex items-center justify-between gap-4">
      <div class="flex items-center gap-1 text-text-secondary">
        <!-- Hidden accessible button for media library -->
        <button
          type="button"
          class="sr-only"
          data-testid="add-media-button"
          @click="handleOpenMediaLibrary"
        >
          {{ t('composer.media.addMedia') }}
        </button>

        <!-- Direct upload trigger -->
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-xl border border-border-visible bg-bg-surface transition hover:border-text-display hover:text-text-display"
          data-testid="composer-upload-trigger"
          @click="emit('open-upload-picker')"
        >
          <ImageIcon class="size-4" />
        </button>

        <!-- Sources dropdown -->
        <Popover v-model:open="isMediaSourcesOpen">
          <PopoverTrigger as-child>
            <button
              type="button"
              class="flex h-10 w-10 items-center justify-center rounded-xl border border-border-visible bg-bg-surface transition hover:border-text-display hover:text-text-display"
              data-testid="composer-sources-trigger"
            >
              <ChevronDown class="size-4" />
            </button>
          </PopoverTrigger>
          <PopoverContent align="start" class="w-60 rounded-2xl border-border-subtle bg-bg-surface p-2">
            <div class="space-y-1">
              <button
                type="button"
                class="flex w-full items-center justify-between rounded-xl px-3 py-2 text-sm text-text-body transition hover:bg-bg-primary"
                data-testid="composer-source-library"
                @click="handleOpenMediaLibrary"
              >
                <span>{{ t('composer.media.sourceLibrary') }}</span>
              </button>
              <button
                v-if="hasUnsplash"
                type="button"
                class="flex w-full items-center justify-between rounded-xl px-3 py-2 text-sm text-text-body transition hover:bg-bg-primary"
                data-testid="composer-source-unsplash"
                @click="handleOpenUnsplashLibrary"
              >
                <span>{{ t('composer.media.sourceExternal') }}</span>
              </button>
            </div>
          </PopoverContent>
        </Popover>

        <!-- Emoji -->
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-xl text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
          title="Open emoji picker"
          @click="emit('emoji-click')"
        >
          <Smile class="size-4" />
        </button>

        <!-- Hashtag -->
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-xl text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
          title="Insert tag"
          @click="emit('hashtag-click')"
        >
          <Hash class="size-4" />
        </button>

        <!-- AI Assist -->
        <button
          type="button"
          class="flex h-10 items-center gap-1 rounded-xl px-2 text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
          title="AI Assist"
          @click="emit('ai-assist-click')"
        >
          <Sparkles class="size-4" />
          <span class="font-mono text-[8px] uppercase tracking-wider font-bold">AI</span>
        </button>
      </div>

      <!-- Character counter -->
      <span
        class="font-mono text-[10px]"
        :class="isTextTooLong ? 'text-error font-bold' : 'text-text-secondary'"
      >
        {{ charsRemaining }} / {{ charLimit }}
      </span>
    </div>
  </div>
</template>
