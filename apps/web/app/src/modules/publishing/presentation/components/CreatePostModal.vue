<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFocusTrap } from '@shared/composables/useFocusTrap'
import { useComposerMediaPicker } from '@modules/publishing/application/useComposerMediaPicker'
import { X } from '@lucide/vue'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import {
  isSocialProvider,
  usePublishingStore,
  type Publication,
} from '@modules/publishing/infrastructure/publishing.store'
import { useMediaStore } from '@modules/media'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'
import PostPreviewPanel from '@modules/publishing/presentation/components/composer/PostPreviewPanel.vue'
import type { LinkedInPreviewModel, PostPreviewMedia } from '@modules/publishing/presentation/components/composer/post-preview.types'
import ComposerMediaPickerShell from '@modules/publishing/presentation/components/composer/ComposerMediaPickerShell.vue'
import MediaProviderPanel from '@modules/publishing/presentation/components/composer/MediaProviderPanel.vue'
import ComposerChannelSelector from '@modules/publishing/presentation/components/composer/ComposerChannelSelector.vue'
import ComposerScheduleFooter from '@modules/publishing/presentation/components/composer/ComposerScheduleFooter.vue'
import ComposerEditor from '@modules/publishing/presentation/components/composer/ComposerEditor.vue'
import ComposerMediaUpload from '@modules/publishing/presentation/components/composer/ComposerMediaUpload.vue'
import ComposerFormSection from '@modules/publishing/presentation/components/composer/ComposerFormSection.vue'
import type { ComposerInlineAttachment } from '@modules/publishing/presentation/components/composer/composer.types'
import { useComposerForm } from '@modules/publishing/presentation/composables/useComposerForm'
import { useComposerMediaUpload } from '@modules/publishing/presentation/composables/useComposerMediaUpload'

const props = withDefaults(
  defineProps<{
    isOpen?: boolean
    initialDate?: string
    editingPublication?: Publication
    provider?: 'unsplash' | null
  }>(),
  {
    isOpen: false,
    provider: null,
  }
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created'): void
  (e: 'updated'): void
}>()

const { t } = useI18n()
const auth = useAuthStore()
const publishingStore = usePublishingStore()
const mediaStore = useMediaStore()

// Composables
const form = useComposerForm({
  editingPublication: () => props.editingPublication ?? null,
  initialDate: props.initialDate,
})
const upload = useComposerMediaUpload()

// Composer media picker
const picker = useComposerMediaPicker({
  mediaStore,
  publishingStore,
  editingPublication: () => props.editingPublication ?? null,
  provider: () => props.provider ?? null,
  initialChannelId: () => form.selectedChannelId.value,
  onAttachmentsChanged: () => {
    form.assetsTouched.value = true
  },
})

// Modal & focus
const modalContainer = ref<HTMLElement | null>(null)
const pickerSessionUploadInput = ref<HTMLInputElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(modalContainer, () => emit('close'))

// Time tracking
const now = ref(new Date())
let timer: ReturnType<typeof setInterval>

function runAiAssist() {
  form.isAiProcessing.value = true
  window.setTimeout(() => {
    form.postText.value += '\n\nProgramado vía @ProfileTailors'
    form.isAiProcessing.value = false
  }, 800)
}

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 60_000)
})

onUnmounted(() => {
  clearInterval(timer)
  picker.stopAllReconciliationPollers()
  upload.clearUploadPreviewBlob()
})

// Lifecycle hooks
watch(
  () => props.isOpen,
  async (open) => {
    if (open) {
      form.clearError()
      await form.initialize()
      picker.syncDraftAttachments(props.editingPublication?.assetIds ?? [])
      if (auth.isAuthenticated) {
        try {
          await mediaStore.loadDanglingAssets()
        } catch {
          // Non-critical
        }
      }
      await nextTick()
      activateFocusTrap()
    } else {
      deactivateFocusTrap()
    }
  },
)

onMounted(async () => {
  if (props.isOpen && form.isEditMode.value) {
    await form.initialize()
    picker.syncDraftAttachments(props.editingPublication?.assetIds ?? [])
  }
})

watch(
  () => mediaStore.selectedAssetIds,
  () => {
    if (form.isEditMode.value) {
      form.assetsTouched.value = true
    }
  },
  { deep: true },
)

watch(
  () => publishingStore.channels,
  (channels) => {
    if (channels.length === 0) {
      form.selectedChannelId.value = null
      return
    }
    const activeIds = new Set(channels.filter(ch => ch.status === 'ACTIVE').map(ch => ch.id))
    if (!activeIds.has(form.selectedChannelId.value ?? '')) {
      form.selectedChannelId.value = channels.find(ch => ch.status === 'ACTIVE')?.id ?? null
    }
  },
)
// Computed
const charLimit = 3000
const selectedChannel = computed(() =>
  publishingStore.channels.find((channel) => channel.id === form.selectedChannelId.value)
  ?? publishingStore.channels[0]
  ?? null,
)
const selectedProviders = computed(() =>
  selectedChannel.value && isSocialProvider(selectedChannel.value.provider)
    ? [selectedChannel.value.provider]
    : [],
)

const selectedChannelInitials = computed(() => {
  const name = selectedChannel.value?.name?.trim()
  if (!name) return 'PT'

  const parts = name.split(/\s+/).filter(Boolean)
  if (parts.length === 1) {
    return (parts[0]?.slice(0, 2) ?? 'PT').toUpperCase()
  }

  return parts
    .slice(0, 2)
    .map((part) => part[0] ?? '')
    .join('')
    .toUpperCase()
})

const canSubmit = computed(() => {
  return (
    !!selectedChannel.value &&
    form.postText.value.trim().length > 0 &&
    form.postText.value.length <= charLimit &&
    !form.isSubmitting.value &&
    !picker.isAttachmentLimitExceeded.value
  )
})

// Methods
async function handleFileSelect(files: File[]) {
  if (picker.isMediaPickerOpen.value) {
    picker.handlePickerUploadSelection(files).catch(() => undefined)
  } else {
    const success = upload.addFiles(files)
    if (success && form.isEditMode.value) {
      form.assetsTouched.value = true
    }
  }
}

async function handleSchedule() {
  if (!canSubmit.value) return

  const _shouldCreateAnother = form.createAnother.value
  form.isSubmitting.value = true

  try {
    const scheduledDate = form.resolveScheduledDate(now.value)
    if (form.submitError.value) return

    const uploadOk = upload.selectedUploadFile.value && upload.uploadTempKey.value
      ? await uploadAndTrackDeferred()
      : true
    if (!uploadOk) return

    const normalizedPostText = form.postText.value.trim()
    const backendScheduleMode = form.resolveScheduleMode(form.scheduleMode.value)

    if (form.isEditMode.value && props.editingPublication) {
      await handleEditSubmit(normalizedPostText, scheduledDate, backendScheduleMode)
    } else {
      await handleCreateSubmit(normalizedPostText, scheduledDate, backendScheduleMode)
    }
  } catch (err) {
    form.submitError.value = err instanceof Error ? err.message : 'Unable to schedule post.'
    console.error('Error scheduling post', err)
  } finally {
    form.isSubmitting.value = false
  }
}

async function uploadAndTrackDeferred(): Promise<boolean> {
  if (!upload.selectedUploadFile.value || !upload.uploadTempKey.value) return true
  upload.isLocalUploadInFlight.value = true
  await nextTick()
  try {
    const asset = await mediaStore.createAndUpload(
      upload.selectedUploadFile.value,
      upload.uploadTempKey.value,
      (pct) => {
        upload.uploadProgress.value = pct
      },
    )
    if (form.isEditMode.value) form.assetsTouched.value = true
    mediaStore.addToSelection(asset.assetId)
    picker.draftAttachmentIds.value = [...picker.draftAttachmentIds.value, asset.assetId]
    upload.uploadTempKey.value = null
    upload.removeFile()
    return true
  } catch {
    form.submitError.value = 'Media upload failed. Please try again.'
    return false
  } finally {
    upload.isLocalUploadInFlight.value = false
  }
}

async function handleEditSubmit(
  normalizedPostText: string,
  scheduledDate: Date | undefined,
  backendScheduleMode: NonNullable<Publication['scheduleMode']>,
) {
  const editingPublication = props.editingPublication
  if (!editingPublication) return

  await publishingStore.updatePost(editingPublication.id, {
    content: normalizedPostText,
    scheduledAt: scheduledDate?.toISOString(),
    priority: form.priorityMode.value,
    scheduleMode: backendScheduleMode,
    ...(form.assetsTouched.value ? { assetIds: [...picker.draftAttachmentIds.value] } : {}),
  })
  emit('updated')
  emit('close')
}

async function handleCreateSubmit(
  normalizedPostText: string,
  scheduledDate: Date | undefined,
  backendScheduleMode: NonNullable<Publication['scheduleMode']>,
) {
  await publishingStore.schedulePost({
    content: normalizedPostText,
    title: 'Post from App',
    channels: selectedProviders.value,
    scheduledAt: scheduledDate?.toISOString(),
    nextSlotAfter: form.scheduleMode.value === 'next' ? now.value.toISOString() : undefined,
    scheduleMode: backendScheduleMode,
    priority: form.priorityMode.value,
    thumbnail: selectedAssetIsImage.value
      ? (upload.uploadPreviewBlob.value ?? selectedAssetPreviewUrl.value ?? undefined)
      : undefined,
    assetIds: [...picker.draftAttachmentIds.value],
    socialAccountId: selectedChannel.value?.accountId,
  })
  emit('created')
  if (form.createAnother.value) {
    form.reset()
    upload.clearUploadPreviewBlob()
    picker.draftAttachmentIds.value = []
    picker.pickerSelectionIds.value = []
    picker.resetPickerSessionTracking()
  } else {
    emit('close')
  }
}

const selectedAssetIsImage = computed(() => {
  const uploadFile = currentUpload.value?.file ?? upload.selectedUploadFile.value
  if (uploadFile) {
    return uploadFile.type.startsWith('image/')
  }

  const first = picker.draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .find((asset) => asset !== undefined)
  if (!first) return false
  return first.mediaType.startsWith('image/')
})

const selectedAssetPreviewUrl = computed<string | null>(() => {
  if (upload.uploadPreviewBlob.value && selectedAssetIsImage.value) {
    return upload.uploadPreviewBlob.value
  }

  const first = picker.draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .find((asset) => asset !== undefined)
  if (!first) return null
  if (!first.mediaType.startsWith('image/')) return null
  return first.previewUrl ? resolveApiUrl(first.previewUrl) : null
})

const selectedPreviewMedia = computed<PostPreviewMedia | null>(() => {
  const uploadFile = currentUpload.value?.file ?? upload.selectedUploadFile.value
  if (uploadFile) {
    const isImage = uploadFile.type.startsWith('image/')

    return {
      kind: isImage ? 'image' : 'video',
      url: isImage ? upload.uploadPreviewBlob.value : null,
      alt: 'Media preview',
      name: uploadFile.name,
    }
  }

  const first = picker.draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .find((asset) => asset !== undefined)
  if (!first) return null

  const isImage = first.mediaType.startsWith('image/')

  return {
    kind: isImage ? 'image' : 'video',
    url: isImage ? selectedAssetPreviewUrl.value : null,
    alt: 'Media preview',
    name: first.originalFilename,
  }
})

const linkedinPreview = computed<LinkedInPreviewModel>(() => ({
  authorName: selectedChannel.value?.name || auth.user?.username || 'Profile Tailors',
  authorHandle: selectedChannel.value?.handle || 'LinkedIn Member',
  authorAvatarUrl: selectedChannel.value?.avatarUrl ?? null,
  authorInitials: selectedChannelInitials.value,
  text: form.postText.value,
  placeholderText: t('composer.seePreviewHere'),
  media: selectedPreviewMedia.value,
}))

const currentUpload = computed(() => {
  const trackedUpload = upload.uploadTempKey.value ? mediaStore.uploads[upload.uploadTempKey.value] : undefined
  if (trackedUpload) {
    return trackedUpload.status === 'done' ? null : trackedUpload
  }

  return (
    mediaStore.uploadList.find(
      (upload) => upload.tempKey.startsWith('modal-upload-') && upload.status !== 'done',
    ) ?? null
  )
})

const normalizedUploadProgress = computed<number | null>(() => {
  if (!currentUpload.value) return null
  return Math.max(0, Math.min(100, currentUpload.value.progress ?? upload.uploadProgress.value ?? 0))
})

const currentUploadStateLabel = computed<string | null>(() => {
  if (currentUpload.value?.status !== 'uploading') return null
  if ((normalizedUploadProgress.value ?? 0) >= 100) {
    return t('composer.media.finishingUpload')
  }
  return t('composer.media.uploadingProgress', {
    progress: Math.round(normalizedUploadProgress.value ?? 0),
  })
})

const composerInlineAttachments = computed<ComposerInlineAttachment[]>(() => {
  const localUploadAttachment: ComposerInlineAttachment[] = upload.selectedUploadFile.value
    ? [
      {
        key: upload.uploadTempKey.value ?? `local-upload-${upload.selectedUploadFile.value.name}`,
        kind: 'local-upload',
        assetId: null,
        name: upload.selectedUploadFile.value.name,
        previewUrl: upload.selectedUploadFile.value.type.startsWith('image/') ? upload.uploadPreviewBlob.value : null,
        isUploading: currentUpload.value?.status === 'uploading',
        uploadProgress: normalizedUploadProgress.value,
        uploadStateLabel: currentUploadStateLabel.value,
      },
    ]
    : []

  const persistedAttachments: ComposerInlineAttachment[] = picker.draftAttachmentAssets.value.map((asset) => ({
    key: asset.assetId,
    kind: 'draft',
    assetId: asset.assetId,
    name: asset.originalFilename ?? asset.assetId,
    previewUrl: asset.previewUrl ? resolveApiUrl(asset.previewUrl) : null,
    isUploading: false,
    uploadProgress: 100,
    uploadStateLabel: null,
  }))

  return [...localUploadAttachment, ...persistedAttachments]
})
</script>

<template>
  <Teleport to="body">
    <!-- biome-ignore lint/a11y/noStaticElementInteractions: overlay backdrop, closes on outside click -->
    <div
      v-if="props.isOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in"
      @click.self="emit('close')"
      @keydown.escape="emit('close')"
    >
      <dialog
        ref="modalContainer"
        open
        aria-modal="true"
        aria-labelledby="create-post-title"
        class="relative m-0 flex h-[min(92vh,750px)] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-surface shadow-2xl animate-zoom-in lg:flex-row"
      >
        <button
          type="button"
          @click="emit('close')"
          class="absolute top-4 right-4 z-50 flex size-8 items-center justify-center rounded-full border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display lg:hidden"
        >
          <X class="size-4" />
        </button>

        <div class="flex min-h-0 flex-1 flex-col space-y-6 border-b border-border-subtle p-6 lg:border-b-0 lg:border-r overflow-hidden">
          <div class="flex items-center justify-between">
            <h3 id="create-post-title" class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
              {{ form.isEditMode ? $t('composer.editTitle') : $t('composer.title') }}
            </h3>
            <button
              type="button"
              @click="emit('close')"
              class="hidden lg:flex size-7 items-center justify-center rounded-xl border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display cursor-pointer"
            >
              <X class="size-3.5" />
            </button>
          </div>

          <ComposerChannelSelector
            :channels="publishingStore.channels.filter(ch => ch.status === 'ACTIVE')"
            :model-value="form.selectedChannelId.value"
            :is-edit-mode="form.isEditMode.value"
            @update:model-value="form.selectedChannelId.value = $event"
          />

          <!-- Editor Section -->
          <ComposerEditor
            :model-value="form.postText.value"
            :char-limit="charLimit"
            :placeholder="$t('composer.placeholder')"
            @update:modelValue="form.postText.value = $event"
            @emoji-click="form.postText.value += ' 🙂'"
            @ai-assist-click="runAiAssist"
            @dragover="(e) => { e.preventDefault(); form.isDropzoneActive.value = true }"
            @dragleave="(e) => { e.preventDefault(); form.isDropzoneActive.value = false }"
            @drop="(e) => { e.preventDefault(); form.isDropzoneActive.value = false; handleFileSelect(upload.extractFilesFromDataTransfer(e.dataTransfer)) }"
            @paste="(e) => { e.preventDefault(); handleFileSelect(upload.extractFilesFromClipboard(e.clipboardData)) }"
          />

          <!-- Media Upload Section -->
          <ComposerMediaUpload
            :attachments="composerInlineAttachments"
            :is-dropzone-active="form.isDropzoneActive.value"
            :is-local-upload-in-flight="upload.isLocalUploadInFlight.value"
            :normalized-upload-progress="normalizedUploadProgress"
            :current-upload-state-label="currentUploadStateLabel"
            :has-unsplash="picker.effectiveProvider.value === 'unsplash'"
            :chars-remaining="charLimit - form.postText.value.length"
            :char-limit="charLimit"
            :is-text-too-long="form.postText.value.length > charLimit"
            @dragover="(e) => { e.preventDefault(); form.isDropzoneActive.value = true }"
            @dragleave="(e) => { e.preventDefault(); form.isDropzoneActive.value = false }"
            @drop="(e) => { e.preventDefault(); form.isDropzoneActive.value = false; handleFileSelect(upload.extractFilesFromDataTransfer(e.dataTransfer)) }"
            @paste="(e) => { e.preventDefault(); handleFileSelect(upload.extractFilesFromClipboard(e.clipboardData)) }"
            @open-upload-picker="pickerSessionUploadInput?.click()"
            @open-media-library="picker.openMediaPicker('library').catch(() => undefined)"
            @open-unsplash-library="picker.openMediaPicker('unsplash').catch(() => undefined)"
            @remove-draft-attachment="(assetId) => { picker.removeDraftAttachment(assetId); form.assetsTouched.value = true }"
            @remove-local-upload="() => { upload.removeFile(); form.assetsTouched.value = true }"
            @file-selected="handleFileSelect"
            @update:isDropzoneActive="form.isDropzoneActive.value = $event"
          />

          <input
            ref="pickerSessionUploadInput"
            type="file"
            class="hidden"
            accept="image/jpeg,image/png,image/gif,image/webp,video/mp4"
            aria-label="Upload media file"
            @change="(e) => handleFileSelect(Array.from((e.target as HTMLInputElement).files ?? []))"
          />

          <!-- Attachment Limit Warning -->
          <p
            v-if="picker.isAttachmentLimitExceeded.value"
            data-testid="attachment-limit-warning"
            class="rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
          >
            {{ t('composer.media.limitWarning', {
              current: picker.draftAttachmentIds.value.length,
              max: Number.isFinite(picker.effectiveAttachmentLimit.value) ? picker.effectiveAttachmentLimit.value : t('composer.media.limitInfinite'),
            }) }}
          </p>

          <!-- Media Picker Shell -->
          <ComposerMediaPickerShell
            :is-open="picker.isMediaPickerOpen.value"
            :active-source="picker.activeMediaPickerSource.value"
            :collection-state="picker.mediaPickerCollectionState.value"
            :assets="picker.pickerAssets.value"
            :provider="picker.effectiveProvider.value"
            :apply-disabled="picker.isPickerSelectionOverLimit.value"
            :apply-disabled-message="picker.isPickerSelectionOverLimit.value ? t('composer.picker.applyDisabledLimit') : null"
            @toggle-asset="picker.togglePickerAsset($event.assetId)"
            @apply-selection="picker.applyPickerSelection()"
            @provider-search="picker.handleProviderSearch"
            @set-active-source="picker.setActiveMediaPickerSource($event.source)"
            @provider-import="picker.handleProviderImport"
            @close="picker.closeMediaPicker()"
          >
            <template v-if="picker.effectiveProvider.value === 'unsplash'" #provider>
              <MediaProviderPanel
                :results="picker.providerResults.value"
                :is-searching="picker.providerSearching.value"
                :search-error="picker.providerSearchError.value"
                @provider-import="picker.handleProviderImport"
              />
            </template>
          </ComposerMediaPickerShell>

          <!-- First Comment Section -->
          <ComposerFormSection
            :model-value="form.firstComment.value"
            @update:modelValue="form.firstComment.value = $event"
          />
        </div>

        <!-- Preview & Schedule Footer -->
        <PostPreviewPanel
          provider="linkedin"
          :title="$t('composer.linkedinPreview')"
          :linkedin-preview="linkedinPreview"
        >
          <template #footer>
            <ComposerScheduleFooter
               :schedule-mode="form.scheduleMode.value"
               :selected-calendar-date="form.selectedCalendarDate.value"
               :schedule-time="form.scheduleTime.value"
               :priority-mode="form.priorityMode.value"
               :create-another="form.createAnother.value"
              :is-edit-mode="form.isEditMode.value"
              :can-submit="canSubmit"
              :is-submitting="form.isSubmitting.value"
              :submit-error="form.submitError.value"
              :now="now"
              @update:schedule-mode="form.scheduleMode.value = $event"
              @update:selected-calendar-date="form.selectedCalendarDate.value = $event"
              @update:schedule-time="form.scheduleTime.value = $event"
              @update:priority-mode="form.priorityMode.value = $event"
              @update:create-another="form.createAnother.value = $event"
              @close="emit('close')"
              @submit="handleSchedule"
            />
          </template>
        </PostPreviewPanel>
      </dialog>
    </div>
  </Teleport>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
.animate-zoom-in {
  animation: zoomIn 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes zoomIn {
  from { transform: scale(0.96); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
