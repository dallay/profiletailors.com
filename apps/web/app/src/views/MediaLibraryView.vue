<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, FileText, Image, Loader2, RefreshCw, Trash2, UploadCloud, Video } from '@lucide/vue'
import { useMediaStore } from '@/stores/media'
import { useAuthStore } from '@/stores/auth'
import { resolveApiUrl } from '@/lib/auth-api'
import type { MediaStatus } from '@/lib/media-api'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'

const mediaStore = useMediaStore()
const authStore = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)
const isRefreshing = ref(false)
const uploadRequiresVerification = ref(false)
const selectedLibraryAssetIds = ref<string[]>([])
const searchQuery = ref('')
const statusFilter = ref<'ALL' | 'READY' | 'PROCESSING' | 'FAILED'>('ALL')
const typeFilter = ref<'ALL' | 'IMAGE' | 'VIDEO' | 'PDF' | 'OTHER'>('ALL')
const sortBy = ref<'newest' | 'oldest' | 'filename-asc' | 'filename-desc' | 'size-desc' | 'size-asc' | 'status'>('newest')

const assets = computed(() =>
  mediaStore.assetIds
    .map((id) => mediaStore.assetsById[id])
    .filter((asset) => asset !== undefined),
)

function isProcessingStatus(status: MediaStatus): boolean {
  return status === 'PENDING_UPLOAD' || status === 'UPLOADING'
}

const visibleAssets = computed(() => {
  const normalizedQuery = searchQuery.value.trim().toLowerCase()

  const filtered = assets.value.filter((asset) => {
    const matchesStatus =
      statusFilter.value === 'ALL' ||
      (statusFilter.value === 'PROCESSING'
        ? isProcessingStatus(asset.status)
        : asset.status === statusFilter.value)
    const matchesType =
      typeFilter.value === 'ALL' ||
      (typeFilter.value === 'IMAGE' && isImage(asset.mediaType)) ||
      (typeFilter.value === 'VIDEO' && isVideo(asset.mediaType)) ||
      (typeFilter.value === 'PDF' && isPdf(asset.mediaType)) ||
      (typeFilter.value === 'OTHER' && !isImage(asset.mediaType) && !isVideo(asset.mediaType) && !isPdf(asset.mediaType))
    const matchesQuery =
      normalizedQuery.length === 0 ||
      asset.assetId.toLowerCase().includes(normalizedQuery) ||
      (asset.originalFilename ?? '').toLowerCase().includes(normalizedQuery) ||
      asset.mediaType.toLowerCase().includes(normalizedQuery)
    return matchesStatus && matchesType && matchesQuery
  })

  return [...filtered].sort((left, right) => {
    switch (sortBy.value) {
      case 'oldest':
        return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
      case 'filename-asc':
        return (left.originalFilename ?? left.assetId).localeCompare(right.originalFilename ?? right.assetId)
      case 'filename-desc':
        return (right.originalFilename ?? right.assetId).localeCompare(left.originalFilename ?? left.assetId)
      case 'size-asc':
        return (left.fileSizeBytes ?? 0) - (right.fileSizeBytes ?? 0)
      case 'size-desc':
        return (right.fileSizeBytes ?? 0) - (left.fileSizeBytes ?? 0)
      case 'status':
        return left.status.localeCompare(right.status)
        default:
        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
    }
  })
})

const allVisibleSelected = computed(() =>
  visibleAssets.value.length > 0 && visibleAssets.value.every((asset) => selectedLibraryAssetIds.value.includes(asset.assetId)),
)

const readyAssets = computed(() => assets.value.filter((asset) => asset.status === 'READY'))
const processingAssets = computed(() =>
  assets.value.filter((asset) => isProcessingStatus(asset.status)),
)
const failedAssets = computed(() => assets.value.filter((asset) => asset.status === 'FAILED'))
const canUploadMedia = computed(() => authStore.isEmailVerified && !uploadRequiresVerification.value)
const showVerificationGuidance = computed(() => !canUploadMedia.value || uploadRequiresVerification.value)

function statusClass(status: MediaStatus) {
  if (isProcessingStatus(status)) {
    return 'border-text-display/30 bg-text-display/10 text-text-display'
  }

  switch (status) {
    case 'READY':
      return 'border-success/30 bg-success/10 text-success'
    case 'FAILED':
      return 'border-error/30 bg-error/10 text-error'
    default:
      return 'border-border-visible bg-bg-primary text-text-secondary'
  }
}

function isImage(mediaType: string) {
  return mediaType.startsWith('image/')
}

function isVideo(mediaType: string) {
  return mediaType.startsWith('video/')
}

function isPdf(mediaType: string) {
  return mediaType === 'application/pdf'
}

function resolveUrl(first: string | null | undefined, second: string | null | undefined): string | null {
  if (first) return resolveApiUrl(first)
  if (second) return resolveApiUrl(second)
  return null
}

function assetPreviewUrl(asset: { downloadUrl?: string | null; previewUrl?: string | null }) {
  return resolveUrl(asset.previewUrl, asset.downloadUrl)
}

function assetDownloadUrl(asset: { downloadUrl?: string | null; previewUrl?: string | null }) {
  return resolveUrl(asset.downloadUrl, asset.previewUrl)
}

function formatFileSize(bytes: number | null | undefined) {
  if (bytes == null || Number.isNaN(bytes)) return null
  if (bytes < 1024) return `${bytes} B`

  const units = ['KB', 'MB', 'GB', 'TB']
  let size = bytes / 1024
  let unitIndex = 0

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }

  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

function triggerDownload(asset: { originalFilename?: string | null; downloadUrl?: string | null; previewUrl?: string | null }) {
  const url = assetDownloadUrl(asset)
  if (!url) return

  const link = document.createElement('a')
  link.href = url
  link.download = asset.originalFilename ?? 'media-asset'
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

function toggleAssetSelection(assetId: string) {
  if (selectedLibraryAssetIds.value.includes(assetId)) {
    selectedLibraryAssetIds.value = selectedLibraryAssetIds.value.filter((id) => id !== assetId)
    return
  }
  selectedLibraryAssetIds.value = [...selectedLibraryAssetIds.value, assetId]
}

function clearAssetSelection() {
  selectedLibraryAssetIds.value = []
}

function toggleSelectAllVisible() {
  if (allVisibleSelected.value) {
    clearAssetSelection()
    return
  }

  selectedLibraryAssetIds.value = visibleAssets.value
    .filter((asset) => asset.status === 'READY' || asset.status === 'FAILED')
    .map((asset) => asset.assetId)
}

async function deletePersistedAsset(assetId: string) {
  try {
    await mediaStore.deletePersistedAsset(assetId)
    selectedLibraryAssetIds.value = selectedLibraryAssetIds.value.filter((id) => id !== assetId)
  } catch {
    // Error state is tracked in mediaStore; selection is preserved on failure
  }
}

async function deleteSelectedAssets() {
  for (const assetId of selectedLibraryAssetIds.value) {
    try {
      await mediaStore.deletePersistedAsset(assetId)
    } catch {
      // Continue deleting other selected assets even if one fails
    }
  }
  clearAssetSelection()
}

async function refreshLibrary() {
  isRefreshing.value = true
  try {
    await mediaStore.loadAssets('READY,PENDING_UPLOAD,UPLOADING,FAILED')
  } finally {
    isRefreshing.value = false
  }
}

async function loadMore() {
  await mediaStore.loadNextPage('READY,PENDING_UPLOAD,UPLOADING,FAILED')
}

function openFilePicker() {
  if (!canUploadMedia.value) return
  fileInput.value?.click()
}

async function uploadFiles(files: File[]) {
  if (!canUploadMedia.value) return
  uploadRequiresVerification.value = false

  for (const file of files) {
    if (uploadRequiresVerification.value) break
    const tempKey = `media-library-${Date.now()}-${file.name}`
    try {
      await mediaStore.createAndUpload(file, tempKey)
    } catch (error) {
      const apiError = error as { status?: number; errorCode?: string; code?: string }
      const errorCode = apiError.errorCode ?? apiError.code
      if (apiError.status === 403 && errorCode === 'EMAIL_VERIFICATION_REQUIRED') {
        uploadRequiresVerification.value = true
      }
      // Other error state is already tracked in mediaStore.uploads
    }
  }
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files?.length) return
  await uploadFiles(Array.from(target.files))
  target.value = ''
}

onMounted(async () => {
  await refreshLibrary()
})
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-8">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div class="space-y-2">
        <h2 class="text-3xl font-light tracking-tight text-text-display">
          {{ $t('nav.media') }}
        </h2>
        <p class="text-sm text-text-secondary">
          {{ $t('media.subtitle') }}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label for="media-library-file-input" class="sr-only">
          Upload media files
        </label>
        <input
          id="media-library-file-input"
          ref="fileInput"
          type="file"
          class="hidden"
          :aria-label="$t('media.uploadAction')"
          accept="image/*,video/mp4,application/pdf"
          multiple
          :disabled="!canUploadMedia"
          @change="handleFileChange"
        />
        <Button type="button" variant="outline" @click="refreshLibrary">
          <RefreshCw :class="['mr-2 size-4', isRefreshing ? 'animate-spin' : '']" />
          {{ $t('media.refresh') }}
        </Button>
        <Button
          type="button"
          data-testid="media-upload-button"
          :disabled="!canUploadMedia"
          @click="openFilePicker"
        >
          <UploadCloud class="mr-2 size-4" />
          {{ $t('media.uploadAction') }}
        </Button>
      </div>
    </div>

    <div
      v-if="showVerificationGuidance"
      data-testid="media-verification-guidance"
      role="alert"
      class="rounded-2xl border border-warning/40 bg-warning/10 px-5 py-4 text-sm text-text-body"
    >
      <p class="font-medium text-text-display">{{ $t('media.verificationRequired') }}</p>
      <p class="mt-1 text-text-secondary">{{ $t('media.verificationGuidance') }}</p>
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <div class="inline-flex items-center gap-2 rounded-full border border-success/30 bg-success/10 px-4 py-2">
        <span class="size-2 rounded-full bg-success" />
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-display">{{ $t('media.readyTitle') }}</span>
        <span class="font-mono text-sm font-medium text-text-display">{{ readyAssets.length }}</span>
      </div>
      <div class="inline-flex items-center gap-2 rounded-full border border-text-display/20 bg-text-display/5 px-4 py-2">
        <span class="size-2 rounded-full bg-text-secondary" />
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-display">{{ $t('media.processingTitle') }}</span>
        <span class="font-mono text-sm font-medium text-text-display">{{ processingAssets.length + mediaStore.pendingUploads.length }}</span>
      </div>
      <div class="inline-flex items-center gap-2 rounded-full border border-error/30 bg-error/10 px-4 py-2">
        <span class="size-2 rounded-full bg-error" />
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-display">{{ $t('media.failedTitle') }}</span>
        <span class="font-mono text-sm font-medium text-text-display">{{ failedAssets.length + mediaStore.failedUploads.length }}</span>
      </div>
    </div>

    <!-- ── Unified Filter Toolbar ── -->
    <div class="flex flex-wrap items-center gap-3 rounded-2xl border border-border-subtle bg-bg-primary/30 px-5 py-4">
      <div class="flex items-center gap-2">
        <input
          id="select-all-visible"
          :checked="allVisibleSelected"
          type="checkbox"
          class="size-4"
          @change="toggleSelectAllVisible"
        />
        <!-- biome-ignore lint/a11y/noLabelWithoutControl: $t() provides accessible text, Biome can't resolve i18n keys statically -->
        <label for="select-all-visible" class="sr-only">{{ $t('media.selectAllVisible') }}</label>
      </div>

      <div class="min-w-0 flex-1 lg:max-w-xs">
        <input
          v-model="searchQuery"
          :aria-label="$t('media.searchLabel')"
          type="search"
          class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
          :placeholder="$t('media.searchPlaceholder')"
        />
      </div>

      <select v-model="statusFilter" data-testid="filter-status" :aria-label="$t('media.statusFilter')" class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display">
        <option value="ALL">{{ $t('media.filterAll') }}</option>
        <option value="READY">READY</option>
        <option value="PROCESSING">PROCESSING</option>
        <option value="FAILED">FAILED</option>
      </select>

      <select v-model="typeFilter" data-testid="filter-type" :aria-label="$t('media.typeFilter')" class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display">
        <option value="ALL">{{ $t('media.filterAll') }}</option>
        <option value="IMAGE">{{ $t('media.typeImage') }}</option>
        <option value="VIDEO">{{ $t('media.typeVideo') }}</option>
        <option value="PDF">{{ $t('media.typePdf') }}</option>
        <option value="OTHER">{{ $t('media.typeOther') }}</option>
      </select>

      <select v-model="sortBy" data-testid="filter-sort" :aria-label="$t('media.sortLabel')" class="rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display">
        <option value="newest">{{ $t('media.sortNewest') }}</option>
        <option value="oldest">{{ $t('media.sortOldest') }}</option>
        <option value="filename-asc">{{ $t('media.sortFilenameAsc') }}</option>
        <option value="filename-desc">{{ $t('media.sortFilenameDesc') }}</option>
        <option value="size-desc">{{ $t('media.sortSizeDesc') }}</option>
        <option value="size-asc">{{ $t('media.sortSizeAsc') }}</option>
        <option value="status">{{ $t('media.sortStatus') }}</option>
      </select>

      <span class="text-xs text-text-secondary">{{ visibleAssets.length }} / {{ assets.length }}</span>
    </div>

    <!-- ── Selection Toolbar ── -->
    <div
      v-if="selectedLibraryAssetIds.length > 0"
      class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-primary/30 bg-primary/10 px-5 py-3"
    >
      <div class="flex items-center gap-3">
        <span class="text-sm font-medium text-text-display">
          {{ selectedLibraryAssetIds.length }} {{ $t('media.selectedCountSuffix') }}
        </span>
        <Button type="button" variant="outline" size="sm" @click="clearAssetSelection">
          {{ $t('media.clearSelection') }}
        </Button>
      </div>
      <div class="flex items-center gap-2">
        <AlertDialog>
          <AlertDialogTrigger as-child>
            <Button type="button" variant="outline" size="sm">
              <Trash2 class="mr-2 size-4" />
              {{ $t('media.deleteSelectedAction') }}
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{{ $t('media.bulkDeleteConfirmTitle') }}</AlertDialogTitle>
              <AlertDialogDescription>
                {{ $t('media.bulkDeleteConfirmBody') }}
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>{{ $t('workspace.cancel') }}</AlertDialogCancel>
              <AlertDialogAction @click="deleteSelectedAssets">
                {{ $t('media.deleteSelectedAction') }}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </div>

    <!-- ── Library content ── -->
    <div class="space-y-5">
      <p v-if="mediaStore.loadError" class="mb-4 rounded-xl border border-error/30 bg-error/10 p-3 text-sm text-error">
          {{ mediaStore.loadError }}
        </p>

        <div v-if="mediaStore.isLoading && assets.length === 0" class="flex items-center gap-2 text-sm text-text-secondary">
          <Loader2 class="size-4 animate-spin" />
          {{ $t('media.loading') }}
        </div>

        <div v-else-if="assets.length === 0" class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/30 p-10 text-center">
          <Image class="mx-auto size-8 text-text-secondary" />
          <p class="mt-3 text-sm text-text-display">{{ $t('media.emptyTitle') }}</p>
          <p class="mt-1 text-xs text-text-secondary">{{ $t('media.emptyBody') }}</p>
        </div>

        <div v-else-if="visibleAssets.length === 0" class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/30 p-10 text-center">
          <Image class="mx-auto size-8 text-text-secondary" />
          <p class="mt-3 text-sm text-text-display">{{ $t('media.noFilteredAssetsTitle') }}</p>
          <p class="mt-1 text-xs text-text-secondary">{{ $t('media.noFilteredAssetsBody') }}</p>
        </div>

        <div v-else class="space-y-5">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            <article
              v-for="asset in visibleAssets"
              :key="asset.assetId"
              class="group relative overflow-hidden rounded-2xl border border-border-subtle bg-bg-primary/40 transition-colors hover:border-text-display/30"
            >
              <!-- Thumbnail -->
              <div class="relative aspect-square overflow-hidden bg-bg-primary/70">
                <!-- Checkbox overlay on hover -->
                <div class="absolute left-3 top-3 z-20 opacity-0 transition-opacity group-hover:opacity-100">
                  <input
                    :checked="selectedLibraryAssetIds.includes(asset.assetId)"
                    :aria-label="$t('media.selectLabel')"
                    type="checkbox"
                    class="size-4 rounded border-border-visible bg-black/60 text-primary focus:ring-primary"
                    @change="toggleAssetSelection(asset.assetId)"
                  />
                </div>

                <!-- Status badge overlay -->
                <span
                  data-testid="status-badge"
                  class="absolute right-3 top-3 z-10 rounded-full px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em]"
                  :class="statusClass(asset.status)"
                >
                  {{ asset.status }}
                </span>

                <!-- Hover actions overlay (bottom) -->
                <div class="absolute bottom-3 right-3 z-20 flex items-center gap-2 opacity-0 transition-opacity group-hover:opacity-100">
                  <Button
                    v-if="assetDownloadUrl(asset)"
                    type="button"
                    variant="outline"
                    size="icon"
                    class="size-8 bg-black/60 text-white hover:bg-black/80"
                    @click="triggerDownload(asset)"
                  >
                    <Download class="size-4" />
                  </Button>
                  <AlertDialog>
                    <AlertDialogTrigger as-child>
                      <Button
                        v-if="asset.status === 'READY' || asset.status === 'FAILED'"
                        type="button"
                        variant="outline"
                        size="icon"
                        class="size-8 bg-black/60 text-white hover:bg-black/80"
                      >
                        <Trash2 class="size-4" />
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>{{ $t('media.deleteConfirmTitle') }}</AlertDialogTitle>
                        <AlertDialogDescription>
                          {{ $t('media.deleteConfirmBody') }}
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>{{ $t('workspace.cancel') }}</AlertDialogCancel>
                        <AlertDialogAction @click="deletePersistedAsset(asset.assetId)">
                          {{ $t('media.deleteAction') }}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                </div>

                <!-- Thumbnail content -->
                <img
                  v-if="isImage(asset.mediaType) && assetPreviewUrl(asset)"
                  :src="assetPreviewUrl(asset) ?? ''"
                  :alt="asset.originalFilename ?? asset.assetId"
                  class="h-full w-full object-cover"
                />
                <video
                  v-else-if="isVideo(asset.mediaType) && assetPreviewUrl(asset)"
                  :src="assetPreviewUrl(asset) ?? ''"
                  class="h-full w-full object-cover"
                  controls
                  preload="metadata"
                >
                  <track kind="captions" src="" label="No captions available">
                </video>
                <iframe
                  v-else-if="isPdf(asset.mediaType) && assetPreviewUrl(asset)"
                  :src="assetPreviewUrl(asset) ?? ''"
                  class="h-full w-full border-0 bg-white"
                  title="PDF preview"
                />
                <div v-else class="flex h-full items-center justify-center px-4">
                  <div class="text-center">
                    <Video v-if="isVideo(asset.mediaType)" class="mx-auto size-8 text-text-secondary" />
                    <FileText v-else-if="isPdf(asset.mediaType)" class="mx-auto size-8 text-text-secondary" />
                    <Image v-else class="mx-auto size-8 text-text-secondary" />
                    <p class="mt-2 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary">
                      {{ asset.mediaType }}
                    </p>
                  </div>
                </div>
              </div>

              <!-- Info below thumbnail -->
              <div class="space-y-1 p-3">
                <p class="truncate text-sm font-medium text-text-display">
                  {{ asset.originalFilename ?? $t('media.untitledAsset') }}
                </p>
                <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                  {{ formatFileSize(asset.fileSizeBytes) ?? '—' }}
                </p>
              </div>
            </article>
          </div>

          <div v-if="mediaStore.nextCursor" class="flex justify-center">
            <Button type="button" variant="outline" @click="loadMore">
              {{ $t('media.loadMore') }}
            </Button>
          </div>
        </div>
    </div>
  </div>
</template>
