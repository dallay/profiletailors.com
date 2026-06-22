<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ChevronDown,
  ChevronUp,
  Download,
  FileText,
  Image,
  Loader2,
  RefreshCw,
  Search,
  Trash2,
  UploadCloud,
  Video,
} from '@lucide/vue'
import { useMediaStore } from '@/stores/media'
import { resolveApiUrl } from '@/lib/auth-api'
import { Badge } from '@/components/ui/badge'
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
const fileInput = ref<HTMLInputElement | null>(null)
const isRefreshing = ref(false)
const selectedLibraryAssetIds = ref<string[]>([])
const searchQuery = ref('')
const statusFilter = ref<'ALL' | 'READY' | 'PROCESSING' | 'FAILED'>('ALL')
const typeFilter = ref<'ALL' | 'IMAGE' | 'VIDEO' | 'PDF' | 'OTHER'>('ALL')
const sortBy = ref<'newest' | 'oldest' | 'filename-asc' | 'filename-desc' | 'size-desc' | 'size-asc' | 'status'>('newest')
const showUploads = ref(true)

const assets = computed(() =>
  mediaStore.assetIds
    .map((id) => mediaStore.assetsById[id])
    .filter((asset) => asset !== undefined),
)

const visibleAssets = computed(() => {
  const normalizedQuery = searchQuery.value.trim().toLowerCase()

  const filtered = assets.value.filter((asset) => {
    const matchesStatus = statusFilter.value === 'ALL' || asset.status === statusFilter.value
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

const allVisibleSelected = computed(() => {
  const deletableVisibleAssets = visibleAssets.value.filter(
    (asset) => asset.status === 'READY' || asset.status === 'FAILED',
  )
  return (
    deletableVisibleAssets.length > 0 &&
    deletableVisibleAssets.every((asset) => selectedLibraryAssetIds.value.includes(asset.assetId))
  )
})

const readyAssets = computed(() => assets.value.filter((asset) => asset.status === 'READY'))
const processingAssets = computed(() => assets.value.filter((asset) => asset.status === 'PROCESSING'))
const failedAssets = computed(() => assets.value.filter((asset) => asset.status === 'FAILED'))

const hasActiveUploads = computed(() => mediaStore.uploadList.length > 0)

function statusClass(status: string) {
  switch (status) {
    case 'READY':
      return 'border-success/30 bg-success/10 text-success'
    case 'PROCESSING':
      return 'border-text-display/30 bg-text-display/10 text-text-display'
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

function formatFileSize(bytes: number | null): string {
  if (bytes === null || bytes === undefined) return '—'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}

function assetContentUrl(asset: { downloadUrl?: string | null; previewUrl?: string | null }) {
  return asset.previewUrl ? resolveApiUrl(asset.previewUrl) : asset.downloadUrl ? resolveApiUrl(asset.downloadUrl) : null
}

function triggerDownload(asset: { originalFilename?: string | null; downloadUrl?: string | null; previewUrl?: string | null }) {
  const url = asset.downloadUrl ? resolveApiUrl(asset.downloadUrl) : null
  if (!url) return

  const link = document.createElement('a')
  link.href = url
  link.download = asset.originalFilename ?? 'media-asset'
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function toggleAssetSelection(assetId: string) {
  const asset = mediaStore.assetsById[assetId]
  if (!asset || (asset.status !== 'READY' && asset.status !== 'FAILED')) {
    return
  }
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
  for (const assetId of [...selectedLibraryAssetIds.value]) {
    try {
      await mediaStore.deletePersistedAsset(assetId)
      selectedLibraryAssetIds.value = selectedLibraryAssetIds.value.filter((id) => id !== assetId)
    } catch {
      // Keep failed deletions selected so the user can retry.
    }
  }
}

async function refreshLibrary() {
  isRefreshing.value = true
  try {
    await mediaStore.loadAssets('READY,PROCESSING,FAILED')
  } finally {
    isRefreshing.value = false
  }
}

async function loadMore() {
  await mediaStore.loadNextPage('READY,PROCESSING,FAILED')
}

function openFilePicker() {
  fileInput.value?.click()
}

async function uploadFiles(files: File[]) {
  for (const file of files) {
    const tempKey = `media-library-${Date.now()}-${file.name}`
    try {
      await mediaStore.createAndUpload(file, tempKey)
    } catch {
      // Error state is already tracked in mediaStore.uploads
    }
  }
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files?.length) return
  await uploadFiles(Array.from(target.files))
  target.value = ''
}

async function retryUpload(tempKey: string) {
  try {
    await mediaStore.retryUpload(tempKey)
  } catch {
    // Error state is already tracked in mediaStore.uploads
  }
}

onMounted(async () => {
  if (assets.value.length === 0) {
    await refreshLibrary()
  }
})
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-6">
    <!-- ── Header: Title + Stats + Actions ── -->
    <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div class="space-y-3">
        <div>
          <h2 class="text-3xl font-light tracking-tight text-text-display">
            {{ $t('nav.media') }}
          </h2>
          <p class="mt-1 text-sm text-text-secondary">
            {{ $t('media.subtitle') }}
          </p>
        </div>

        <!-- Stats pills -->
        <div class="flex flex-wrap items-center gap-2">
          <Badge variant="default" class="h-6 gap-1.5 rounded-full px-3 text-[11px] font-medium">
            <span class="size-1.5 rounded-full bg-current" />
            {{ readyAssets.length }} {{ $t('media.readyStatus') }}
          </Badge>
          <Badge variant="secondary" class="h-6 gap-1.5 rounded-full px-3 text-[11px] font-medium">
            <span class="size-1.5 rounded-full bg-current" />
            {{ processingAssets.length + mediaStore.pendingUploads.length }} {{ $t('media.processingStatus') }}
          </Badge>
          <Badge variant="destructive" class="h-6 gap-1.5 rounded-full px-3 text-[11px] font-medium">
            <span class="size-1.5 rounded-full bg-current" />
            {{ failedAssets.length + mediaStore.failedUploads.length }} {{ $t('media.failedStatus') }}
          </Badge>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <label for="media-library-file-input" class="sr-only">
          Upload media files
        </label>
        <input
          id="media-library-file-input"
          ref="fileInput"
          type="file"
          class="hidden"
          aria-label="Upload media files"
          accept="image/*,video/mp4,application/pdf"
          multiple
          @change="handleFileChange"
        />
        <Button type="button" variant="outline" size="icon" @click="refreshLibrary" :disabled="isRefreshing" :aria-label="$t('media.refreshLibrary')">
          <RefreshCw :class="['size-4', isRefreshing ? 'animate-spin' : '']" />
        </Button>
        <Button type="button" @click="openFilePicker">
          <UploadCloud class="mr-2 size-4" />
          {{ $t('media.uploadAction') }}
        </Button>
      </div>
    </div>

    <!-- ── Filter bar ── -->
    <div class="flex flex-col gap-3 rounded-xl border border-border-subtle bg-bg-primary/30 p-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="flex flex-1 flex-wrap items-center gap-2">
        <!-- Search -->
        <div class="relative min-w-0 flex-1 sm:max-w-xs">
          <Search class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-secondary" />
          <input
            v-model="searchQuery"
            :aria-label="$t('media.searchLabel')"
            type="search"
            class="w-full rounded-lg border border-border-visible bg-bg-surface py-2 pl-9 pr-3 text-sm text-text-display placeholder:text-text-secondary/60 focus:border-text-display/40 focus:outline-none"
            :placeholder="$t('media.searchPlaceholder')"
          />
        </div>

        <!-- Status filter -->
        <select
          v-model="statusFilter"
          data-testid="filter-status"
          :aria-label="$t('media.statusFilter')"
          class="rounded-lg border border-border-visible bg-bg-surface px-3 py-2 text-xs text-text-display focus:border-text-display/40 focus:outline-none"
        >
          <option value="ALL">Status: All</option>
          <option value="READY">Ready</option>
          <option value="PROCESSING">Processing</option>
          <option value="FAILED">Failed</option>
        </select>

        <!-- Type filter -->
        <select
          v-model="typeFilter"
          data-testid="filter-type"
          :aria-label="$t('media.typeFilter')"
          class="rounded-lg border border-border-visible bg-bg-surface px-3 py-2 text-xs text-text-display focus:border-text-display/40 focus:outline-none"
        >
          <option value="ALL">Type: All</option>
          <option value="IMAGE">Images</option>
          <option value="VIDEO">Video</option>
          <option value="PDF">PDF</option>
          <option value="OTHER">Other</option>
        </select>

        <!-- Sort -->
        <select
          v-model="sortBy"
          data-testid="filter-sort"
          :aria-label="$t('media.sortLabel')"
          class="rounded-lg border border-border-visible bg-bg-surface px-3 py-2 text-xs text-text-display focus:border-text-display/40 focus:outline-none"
        >
          <option value="newest">Newest</option>
          <option value="oldest">Oldest</option>
          <option value="filename-asc">Name A-Z</option>
          <option value="filename-desc">Name Z-A</option>
          <option value="size-desc">Largest</option>
          <option value="size-asc">Smallest</option>
          <option value="status">Status</option>
        </select>
      </div>

      <div class="flex items-center gap-2 text-xs text-text-secondary">
        <span>{{ visibleAssets.length }} / {{ assets.length }}</span>
      </div>
    </div>

    <!-- ── Selection toolbar (visible when items selected) ── -->
    <div
      v-if="selectedLibraryAssetIds.length > 0"
      class="flex items-center justify-between rounded-xl border border-text-display/20 bg-text-display/5 p-3"
    >
      <div class="flex items-center gap-3">
        <input
          id="select-all-visible"
          :checked="allVisibleSelected"
          type="checkbox"
          class="size-4"
          @change="toggleSelectAllVisible"
        />
        <label for="select-all-visible" class="text-sm text-text-display">
          {{ selectedLibraryAssetIds.length }} selected
        </label>
        <Button type="button" variant="ghost" size="sm" class="text-xs" @click="clearAssetSelection">
          Clear selection
        </Button>
      </div>

      <AlertDialog>
        <AlertDialogTrigger as-child>
          <Button type="button" variant="outline" size="sm" class="text-xs" :disabled="!visibleAssets.some((asset) => asset.status === 'READY' || asset.status === 'FAILED')">
            <Trash2 class="mr-1.5 size-3.5" />
            Delete selected
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

    <!-- ── Upload progress (collapsible) ── -->
    <div
      v-if="hasActiveUploads"
      class="overflow-hidden rounded-xl border border-border-subtle bg-bg-primary/30"
    >
      <button
        type="button"
        class="flex w-full items-center justify-between px-4 py-3 text-left text-sm text-text-secondary hover:text-text-display"
        @click="showUploads = !showUploads"
      >
        <span class="font-medium">{{ $t('media.activeUploadsTitle') }} ({{ mediaStore.uploadList.length }})</span>
        <ChevronDown v-if="!showUploads" class="size-4" />
        <ChevronUp v-else class="size-4" />
      </button>

      <div v-if="showUploads" class="space-y-2 border-t border-border-subtle px-4 pb-4 pt-3">
        <div
          v-for="upload in mediaStore.uploadList"
          :key="upload.tempKey"
          class="space-y-1.5"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm text-text-display">{{ upload.file.name }}</p>
              <p class="font-mono text-[10px] uppercase tracking-[0.08em] text-text-secondary">{{ upload.status }}</p>
              <p v-if="upload.errorDetail" class="mt-0.5 text-xs text-error">{{ upload.errorDetail }}</p>
            </div>
            <div class="flex shrink-0 items-center gap-1.5">
              <Button
                v-if="upload.status === 'failed' || upload.status === 'conflict'"
                type="button"
                variant="ghost"
                size="sm"
                class="h-7 text-xs"
                @click="retryUpload(upload.tempKey)"
              >
                {{ $t('media.retry') }}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                class="h-7 text-xs"
                @click="mediaStore.dismissUpload(upload.tempKey)"
              >
                {{ $t('media.dismiss') }}
              </Button>
            </div>
          </div>
          <div class="h-1 overflow-hidden rounded-full bg-border-visible">
            <div
              class="h-full rounded-full bg-text-display transition-all duration-300"
              :style="{ width: `${upload.progress}%` }"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- ── Error banner ── -->
    <p
      v-if="mediaStore.loadError"
      class="rounded-xl border border-error/30 bg-error/10 p-3 text-sm text-error"
    >
      {{ mediaStore.loadError }}
    </p>

    <!-- ── Loading state ── -->
    <div
      v-if="mediaStore.isLoading && assets.length === 0"
      class="flex items-center justify-center gap-2 py-20 text-sm text-text-secondary"
    >
      <Loader2 class="size-5 animate-spin" />
      {{ $t('media.loading') }}
    </div>

    <!-- ── Empty state (no assets at all) ── -->
    <div
      v-else-if="assets.length === 0"
      class="flex flex-col items-center justify-center gap-4 py-20"
    >
      <div class="flex size-16 items-center justify-center rounded-2xl border border-dashed border-border-visible bg-bg-primary/40">
        <Image class="size-8 text-text-secondary" />
      </div>
      <div class="text-center">
        <p class="text-lg text-text-display">{{ $t('media.emptyTitle') }}</p>
        <p class="mt-1 text-sm text-text-secondary">{{ $t('media.emptyBody') }}</p>
      </div>
      <Button type="button" variant="outline" @click="openFilePicker" class="mt-2">
        <UploadCloud class="mr-2 size-4" />
        {{ $t('media.uploadAction') }}
      </Button>
    </div>

    <!-- ── Empty state (filtered, no results) ── -->
    <div
      v-else-if="visibleAssets.length === 0"
      class="flex flex-col items-center justify-center gap-4 py-20"
    >
      <div class="flex size-16 items-center justify-center rounded-2xl border border-dashed border-border-visible bg-bg-primary/40">
        <Search class="size-8 text-text-secondary" />
      </div>
      <div class="text-center">
        <p class="text-lg text-text-display">{{ $t('media.noFilteredAssetsTitle') }}</p>
        <p class="mt-1 text-sm text-text-secondary">{{ $t('media.noFilteredAssetsBody') }}</p>
      </div>
      <Button type="button" variant="outline" size="sm" @click="searchQuery = ''; statusFilter = 'ALL'; typeFilter = 'ALL'" class="mt-2">
        Reset filters
      </Button>
    </div>

    <!-- ── Asset gallery grid ── -->
    <div v-else class="space-y-6">
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 md:gap-4 lg:grid-cols-4">
        <article
          v-for="asset in visibleAssets"
          :key="asset.assetId"
          class="group relative overflow-hidden rounded-xl border border-border-subtle bg-bg-primary/40 transition-all duration-200 hover:border-text-display/20 hover:bg-bg-primary/60 hover:shadow-lg hover:shadow-black/20"
        >
          <!-- Thumbnail area (aspect-square) -->
          <div class="relative aspect-square overflow-hidden bg-bg-surface/80">
            <!-- Selection checkbox (top-left) — visible on hover or when selected -->
            <div
              class="absolute left-2.5 top-2.5 z-20 transition-opacity duration-150"
              :class="selectedLibraryAssetIds.includes(asset.assetId) ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 group-focus-within:opacity-100'"
            >
              <input
                :checked="selectedLibraryAssetIds.includes(asset.assetId)"
                :aria-label="$t('media.selectLabel')"
                :disabled="asset.status !== 'READY' && asset.status !== 'FAILED'"
                type="checkbox"
                class="size-4 cursor-pointer accent-primary"
                @change="toggleAssetSelection(asset.assetId)"
              />
            </div>

            <!-- Status badge (top-right) -->
            <div class="absolute right-2.5 top-2.5 z-10">
              <span
                class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.08em] backdrop-blur-sm"
                :class="statusClass(asset.status)"
              >
                <span class="size-1.5 rounded-full bg-current" />
                {{ asset.status === 'READY' ? $t('media.readyStatus') : asset.status === 'PROCESSING' ? $t('media.processingStatus') : $t('media.failedStatus') }}
              </span>
            </div>

            <!-- File type icon overlay (center) for non-image or when no preview -->
            <div
              v-if="!isImage(asset.mediaType) || !assetContentUrl(asset)"
              class="absolute inset-0 z-10 flex items-center justify-center"
            >
              <div class="flex flex-col items-center gap-1.5">
                <Video v-if="isVideo(asset.mediaType)" class="size-8 text-text-secondary" />
                <FileText v-else-if="isPdf(asset.mediaType)" class="size-8 text-text-secondary" />
                <Image v-else class="size-8 text-text-secondary" />
                <span
                  v-if="!isImage(asset.mediaType)"
                  class="font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary"
                >
                  {{ asset.mediaType.split('/').pop() }}
                </span>
              </div>
            </div>

            <!-- Image thumbnail -->
            <img
              v-if="isImage(asset.mediaType) && assetContentUrl(asset)"
              :src="assetContentUrl(asset) ?? ''"
              :alt="asset.originalFilename ?? asset.assetId"
              class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
              loading="lazy"
            />

            <!-- Video element (hidden — shows icon instead) -->
            <video
              v-if="isVideo(asset.mediaType) && assetContentUrl(asset)"
              :src="assetContentUrl(asset) ?? ''"
              class="hidden"
              preload="none"
            />

            <!-- Hover overlay actions -->
            <div
              class="absolute inset-x-0 bottom-0 z-20 flex translate-y-full items-center justify-center gap-2 bg-gradient-to-t from-black/70 via-black/30 to-transparent p-3 pt-8 transition-transform duration-200 group-hover:translate-y-0 group-focus-within:translate-y-0"
            >
              <Button
                v-if="assetContentUrl(asset)"
                type="button"
                variant="ghost"
                size="sm"
                class="h-8 w-8 rounded-full bg-white/10 p-0 text-white backdrop-blur-sm hover:bg-white/20"
                @click="triggerDownload(asset)"
                :title="$t('media.downloadAction')"
              >
                <Download class="size-4" />
              </Button>
              <AlertDialog>
                <AlertDialogTrigger as-child>
                  <Button
                    v-if="asset.status === 'READY' || asset.status === 'FAILED'"
                    type="button"
                    variant="ghost"
                    size="sm"
                    class="h-8 w-8 rounded-full bg-white/10 p-0 text-white backdrop-blur-sm hover:bg-white/20"
                    :title="$t('media.deleteAction')"
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
          </div>

          <!-- Metadata row (below thumbnail) -->
          <div class="space-y-1 px-3 py-2.5">
            <p class="truncate text-sm font-medium text-text-display">
              {{ asset.originalFilename ?? $t('media.untitledAsset') }}
            </p>
            <div class="flex items-center gap-3 font-mono text-[10px] uppercase tracking-[0.06em] text-text-secondary">
              <span>{{ formatFileSize(asset.fileSizeBytes) }}</span>
              <span class="size-1 rounded-full bg-border-visible" />
              <span class="truncate">{{ asset.mediaType.split('/').pop() }}</span>
            </div>
          </div>
        </article>
      </div>

      <!-- Load more -->
      <div v-if="mediaStore.nextCursor" class="flex justify-center pb-4">
        <Button type="button" variant="outline" size="lg" @click="loadMore" class="min-w-40">
          {{ $t('media.loadMore') }}
        </Button>
      </div>
    </div>
  </div>
</template>
