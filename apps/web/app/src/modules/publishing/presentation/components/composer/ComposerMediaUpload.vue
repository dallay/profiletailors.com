<script setup lang="ts">
import { computed, ref, } from 'vue'
import type { ComposerInlineAttachment } from '@modules/publishing/presentation/components/composer/composer.types'

const _COMPOSER_SUPPORTED_MEDIA_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'video/mp4',
])

interface Props {
  attachments: ComposerInlineAttachment[]
  isDropzoneActive?: boolean
  isLocalUploadInFlight?: boolean
  normalizedUploadProgress?: number | null
  currentUploadStateLabel?: string | null
  hasUnsplash?: boolean
  charsRemaining: number
  charLimit: number
  isTextTooLong: boolean
  isEditMode?: boolean
}

interface Emits {
  (e: 'dragover', event: DragEvent): void
  (e: 'dragleave', event: DragEvent): void
  (e: 'drop', event: DragEvent): void
  (e: 'paste', event: ClipboardEvent): void
  (e: 'open-upload-picker'): void
  (e: 'open-media-library'): void
  (e: 'open-unsplash-library'): void
  (e: 'remove-draft-attachment', assetId: string): void
  (e: 'remove-local-upload'): void
  (e: 'file-selected', files: File[]): void
  (e: 'update:isDropzoneActive', value: boolean): void
}

const props = withDefaults(defineProps<Props>(), {
  isDropzoneActive: false,
  isLocalUploadInFlight: false,
  normalizedUploadProgress: null,
  currentUploadStateLabel: null,
  hasUnsplash: false,
  isEditMode: false,
})

const emit = defineEmits<Emits>()
const pickerSessionUploadInput = ref<HTMLInputElement | null>(null)
const visibleInlineAttachments = computed(() => props.attachments.slice(0, 3))
const hiddenInlineAttachmentCount = computed(() =>
  Math.max(0, props.attachments.length - visibleInlineAttachments.value.length),
)

function _openUploadPicker() {
  pickerSessionUploadInput.value?.click()
}

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.length) {
    emit('file-selected', Array.from(target.files))
    target.value = ''
  }
}
</script>

<template>
  <section
    aria-label="Media upload zone"
    class="space-y-4"
    data-testid="composer-inline-dropzone"
    @dragover="emit('dragover', $event)"
    @dragleave="emit('dragleave', $event)"
    @drop="emit('drop', $event)"
    @paste="emit('paste', $event)"
  >
    <!-- Attachments Grid -->
    <div v-if="attachments.length > 0" class="space-y-3">
      <div class="grid grid-cols-3 gap-3">
        <div
          v-for="attachment in visibleInlineAttachments"
          :key="attachment.key"
          class="relative group"
        >
          <div
            class="aspect-square rounded-lg overflow-hidden border border-border-visible bg-bg-secondary"
            :data-testid="attachment.kind === 'local-upload' ? 'inline-local-upload' : undefined"
            :title="attachment.kind === 'local-upload' ? attachment.name : undefined"
          >
            <img
              v-if="attachment.previewUrl"
              :src="attachment.previewUrl"
              :alt="attachment.name"
              class="w-full h-full object-cover"
            />
            <div v-else class="w-full h-full flex items-center justify-center bg-bg-secondary">
              <span class="text-xs text-text-secondary">{{ attachment.kind === 'draft' ? 'Media' : 'Uploading...' }}</span>
            </div>

            <!-- Progress -->
            <div
              v-if="attachment.isUploading && attachment.uploadProgress !== null"
              data-testid="inline-upload-overlay"
              class="absolute inset-0 bg-black/50 flex items-center justify-center"
            >
              <div class="text-center">
                <div class="text-sm font-semibold text-white mb-2">{{ attachment.uploadStateLabel ?? `${attachment.uploadProgress}%` }}</div>
                <div class="w-12 h-1 bg-white/30 rounded-full overflow-hidden">
                  <div
                    class="h-full bg-white rounded-full transition-all"
                    :style="{ width: `${attachment.uploadProgress}%` }"
                  ></div>
                </div>
              </div>
            </div>

            <!-- Remove button -->
            <button
              v-if="!attachment.isUploading"
              :data-testid="attachment.kind === 'draft' ? `attachment-remove-${attachment.assetId}` : undefined"
              type="button"
              @click="
                attachment.kind === 'draft' && attachment.assetId
                  ? emit('remove-draft-attachment', attachment.assetId)
                  : emit('remove-local-upload')
              "
              class="absolute top-1 right-1 hidden group-hover:flex size-6 items-center justify-center rounded-lg bg-error/80 text-white hover:bg-error transition-colors"
              aria-label="Remove attachment"
            >
              ✕
            </button>
          </div>
          <p v-if="attachment.kind === 'draft'" class="text-xs text-text-secondary mt-1 truncate">{{ attachment.name }}</p>
        </div>
      </div>

      <!-- Hidden attachments indicator -->
      <div
        v-if="hiddenInlineAttachmentCount > 0"
        data-testid="inline-attachment-overflow"
        class="text-xs text-text-secondary"
      >
        +{{ hiddenInlineAttachmentCount }} more {{ hiddenInlineAttachmentCount === 1 ? 'file' : 'files' }}
      </div>
    </div>

    <p v-if="isLocalUploadInFlight || currentUploadStateLabel" class="text-xs text-text-secondary">
      {{ $t('composer.media.keepEditingWhileUploading') }}
    </p>

    <!-- Upload actions -->
    <div class="flex flex-wrap gap-2">
      <button
        type="button"
        @click="emit('open-upload-picker')"
        class="px-3 py-2 rounded-lg bg-bg-secondary hover:bg-bg-tertiary text-text-display text-xs font-medium transition-colors"
      >
        📤 {{ $t('composer.media.upload') }}
      </button>
      <button
        data-testid="add-media-button"
        type="button"
        @click="emit('open-media-library')"
        class="px-3 py-2 rounded-lg bg-bg-secondary hover:bg-bg-tertiary text-text-display text-xs font-medium transition-colors"
      >
        📷 {{ $t('composer.media.addMedia') }}
      </button>
      <button
        v-if="hasUnsplash"
        type="button"
        @click="emit('open-unsplash-library')"
        class="px-3 py-2 rounded-lg bg-bg-secondary hover:bg-bg-tertiary text-text-display text-xs font-medium transition-colors"
      >
        🌅 {{ $t('composer.media.unsplash') }}
      </button>
    </div>

    <!-- Hidden file input -->
    <input
      data-testid="picker-upload-input"
      ref="pickerSessionUploadInput"
      type="file"
      class="hidden"
      accept="image/jpeg,image/png,image/gif,image/webp,video/mp4"
      aria-label="Upload media file"
      @change="handleFileSelect"
    />

    <!-- Dropzone overlay feedback -->
    <transition name="fade">
      <div
        v-if="isDropzoneActive"
        class="absolute inset-0 rounded-[24px] border-2 border-dashed border-primary bg-primary/5 flex items-center justify-center pointer-events-none"
      >
        <span class="text-sm font-medium text-primary">{{ $t('composer.media.dropHere') }}</span>
      </div>
    </transition>
  </section>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
