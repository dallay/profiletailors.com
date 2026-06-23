<script setup lang="ts">
/**
 * UploadProgressToast
 *
 * Global grouped upload progress toast.
 * - Visible whenever there are active uploads (uploading, failed, conflict).
 * - Expandable to show per-file progress.
 * - Auto-dismisses after success; stays open on errors.
 * - Placed in AppShell so it survives route navigation.
 */
import { computed, ref, watch } from 'vue'
import { Upload, CheckCircle, AlertCircle, XCircle, ChevronDown, ChevronUp, X } from '@lucide/vue'
import { useMediaStore } from '@/stores/media'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const mediaStore = useMediaStore()

const expanded = ref(false)

// Show toast only when there is at least one upload still in-progress or with issues.
const visible = computed(() => mediaStore.uploadList.length > 0)

// Per-file state
const uploading = computed(() => mediaStore.uploadList.filter((u) => u.status === 'uploading'))
const done = computed(() => mediaStore.uploadList.filter((u) => u.status === 'done'))
const failed = computed(() => mediaStore.uploadList.filter((u) => u.status === 'failed' || u.status === 'conflict'))

// Aggregate state of the whole batch
const batchState = computed<'uploading' | 'done' | 'partial' | 'failed'>(() => {
  if (uploading.value.length > 0) return 'uploading'
  if (done.value.length > 0 && failed.value.length === 0) return 'done'
  if (done.value.length > 0 && failed.value.length > 0) return 'partial'
  return 'failed'
})

// Auto-dismiss: if all done, hide after 3s
const dismissTimer = ref<ReturnType<typeof setTimeout> | null>(null)

watch(
  () => batchState.value,
  (state) => {
    if (state === 'done') {
      dismissTimer.value = setTimeout(() => {
        mediaStore.clearUploads()
      }, 3000)
    } else {
      if (dismissTimer.value !== null) {
        clearTimeout(dismissTimer.value)
        dismissTimer.value = null
      }
    }
  },
)

function dismiss() {
  mediaStore.clearUploads()
}

function retryUpload(tempKey: string) {
  mediaStore.retryUpload(tempKey).catch(() => {
    // Error already tracked in the store
  })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="toast-slide">
      <div
        v-if="visible"
        class="fixed bottom-6 right-6 z-50 w-80 overflow-hidden rounded-xl border border-border-visible bg-bg-surface shadow-2xl"
        role="status"
        aria-live="polite"
      >
        <!-- ── Header ── -->
        <!-- biome-ignore lint/a11y/useSemanticElements: the header div role="button" is correct — it contains nested interactive controls (buttons), making a <button> wrapper invalid per HTML spec (interactive content inside button) -->
        <div
          class="flex cursor-pointer items-center justify-between gap-3 px-4 py-3"
          role="button"
          tabindex="0"
          :aria-expanded="expanded"
          @click="expanded = !expanded"
          @keydown.enter.prevent="expanded = !expanded"
          @keydown.space.prevent="expanded = !expanded"
        >
          <!-- Icon + title -->
          <div class="flex min-w-0 items-center gap-3">
            <!-- Uploading spinner -->
            <Upload
              v-if="batchState === 'uploading'"
              class="size-4 shrink-0 animate-pulse text-text-secondary"
            />
            <!-- All good -->
            <CheckCircle
              v-else-if="batchState === 'done'"
              class="size-4 shrink-0 text-success"
            />
            <!-- Partial -->
            <AlertCircle
              v-else-if="batchState === 'partial'"
              class="size-4 shrink-0 text-warning"
            />
            <!-- All failed -->
            <XCircle v-else class="size-4 shrink-0 text-error" />

            <!-- Title -->
            <span class="min-w-0 truncate text-sm font-medium text-text-display">
              <template v-if="batchState === 'uploading'">
                {{ t('media.uploadProgress.uploading', { count: mediaStore.uploadList.length }) }}
              </template>
              <template v-else-if="batchState === 'done'">
                {{ t('media.uploadProgress.uploaded', { count: done.length }) }}
              </template>
              <template v-else-if="batchState === 'partial'">
                {{ t('media.uploadProgress.partial', {
                  uploaded: done.length,
                  failed: failed.length,
                }) }}
              </template>
              <template v-else>
                {{ t('media.uploadProgress.failed', { count: failed.length }) }}
              </template>
            </span>
          </div>

          <!-- Controls — each button handles its own click propagation -->
          <div class="flex shrink-0 items-center gap-1">
            <!-- Expand/collapse -->
            <button
              type="button"
              class="rounded p-1 text-text-secondary hover:bg-bg-primary hover:text-text-display"
              :aria-label="expanded ? 'Collapse' : 'Expand'"
              @click="expanded = !expanded"
            >
              <ChevronDown v-if="!expanded" class="size-4" />
              <ChevronUp v-else class="size-4" />
            </button>

            <!-- Dismiss -->
            <button
              type="button"
              class="rounded p-1 text-text-secondary hover:bg-bg-primary hover:text-text-display"
              aria-label="Dismiss"
              @click.stop="dismiss"
            >
              <X class="size-4" />
            </button>
          </div>
        </div>

        <!-- ── Overall progress bar ── -->
        <div
          v-if="batchState === 'uploading'"
          class="h-1 bg-bg-primary"
        >
          <div
            class="h-full bg-text-display transition-all duration-300"
            :style="{ width: `${Math.round((done.length / mediaStore.uploadList.length) * 100)}%` }"
          />
        </div>

        <!-- ── Expanded detail ── -->
        <Transition name="detail-expand">
          <div
            v-if="expanded"
            class="max-h-60 overflow-y-auto border-t border-border-visible"
          >
            <div
              v-for="upload in mediaStore.uploadList"
              :key="upload.tempKey"
              class="flex items-center gap-3 border-b border-border-visible/50 px-4 py-2.5 last:border-b-0"
            >
              <!-- File name -->
              <div class="min-w-0 flex-1">
                <p class="truncate text-xs text-text-display">{{ upload.file.name }}</p>

                <!-- Status text -->
                <p
                  v-if="upload.status === 'uploading'"
                  class="font-mono text-[10px] uppercase tracking-wide text-text-secondary"
                >
                  {{ upload.progress }}%
                </p>
                <p
                  v-else-if="upload.status === 'done'"
                  class="font-mono text-[10px] uppercase tracking-wide text-success"
                >
                  {{ t('media.uploadProgress.done') }}
                </p>
                <p
                  v-else-if="upload.errorDetail"
                  class="truncate font-mono text-[10px] uppercase tracking-wide text-error"
                  :title="upload.errorDetail"
                >
                  {{ upload.errorTitle ?? t('media.uploadProgress.failed') }}
                </p>
              </div>

              <!-- Progress bar (uploading) -->
              <div
                v-if="upload.status === 'uploading'"
                class="h-1 w-16 shrink-0 overflow-hidden rounded-full bg-bg-primary"
              >
                <div
                  class="h-full bg-text-display transition-all duration-300"
                  :style="{ width: `${upload.progress}%` }"
                />
              </div>

              <!-- Retry button (failed) -->
              <button
                v-if="upload.status === 'failed' || upload.status === 'conflict'"
                type="button"
                class="shrink-0 rounded px-2 py-0.5 text-xs text-text-secondary ring-1 ring-border-visible hover:bg-bg-primary hover:text-text-display"
                @click.stop="retryUpload(upload.tempKey)"
              >
                {{ t('media.uploadProgress.retry') }}
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Slide in/out from bottom-right */
.toast-slide-enter-active,
.toast-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.toast-slide-enter-from,
.toast-slide-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

/* Expanded detail: smooth height */
.detail-expand-enter-active,
.detail-expand-leave-active {
  transition: max-height 0.2s ease, opacity 0.2s ease;
  overflow: hidden;
}
.detail-expand-enter-from,
.detail-expand-leave-to {
  max-height: 0;
  opacity: 0;
}
.detail-expand-enter-to,
.detail-expand-leave-from {
  max-height: 240px;
  opacity: 1;
}
</style>
