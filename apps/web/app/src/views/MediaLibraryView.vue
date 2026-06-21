<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, FileText, Image, Loader2, RefreshCw, Trash2, UploadCloud, Video } from '@lucide/vue'
import { useMediaStore } from '@/stores/media'
import { resolveApiUrl } from '@/lib/auth-api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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

const allVisibleSelected = computed(() =>
  visibleAssets.value.length > 0 && visibleAssets.value.every((asset) => selectedLibraryAssetIds.value.includes(asset.assetId)),
)

const readyAssets = computed(() => assets.value.filter((asset) => asset.status === 'READY'))
const processingAssets = computed(() => assets.value.filter((asset) => asset.status === 'PROCESSING'))
const failedAssets = computed(() => assets.value.filter((asset) => asset.status === 'FAILED'))

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

function assetContentUrl(asset: { downloadUrl?: string | null; previewUrl?: string | null }) {
  return asset.previewUrl ? resolveApiUrl(asset.previewUrl) : asset.downloadUrl ? resolveApiUrl(asset.downloadUrl) : null
}

function triggerDownload(asset: { originalFilename?: string | null; downloadUrl?: string | null; previewUrl?: string | null }) {
  const url = assetContentUrl(asset)
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

  selectedLibraryAssetIds.value = visibleAssets.value.map((asset) => asset.assetId)
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
    await mediaStore.deletePersistedAsset(assetId)
  }
  clearAssetSelection()
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
        <input
          ref="fileInput"
          type="file"
          class="hidden"
          accept="image/*,video/mp4,application/pdf"
          multiple
          @change="handleFileChange"
        />
        <Button type="button" variant="outline" @click="refreshLibrary">
          <RefreshCw :class="['mr-2 size-4', isRefreshing ? 'animate-spin' : '']" />
          {{ $t('media.refresh') }}
        </Button>
        <Button type="button" @click="openFilePicker">
          <UploadCloud class="mr-2 size-4" />
          {{ $t('media.uploadAction') }}
        </Button>
      </div>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <Card>
        <CardHeader>
          <CardTitle class="label-mono text-text-display text-[10px]">{{ $t('media.readyTitle') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-3xl font-light text-text-display">{{ readyAssets.length }}</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle class="label-mono text-text-display text-[10px]">{{ $t('media.processingTitle') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-3xl font-light text-text-display">{{ processingAssets.length + mediaStore.pendingUploads.length }}</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle class="label-mono text-text-display text-[10px]">{{ $t('media.failedTitle') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-3xl font-light text-text-display">{{ failedAssets.length + mediaStore.failedUploads.length }}</p>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader>
        <CardTitle class="label-mono text-text-display text-[10px]">{{ $t('media.activeUploadsTitle') }}</CardTitle>
      </CardHeader>
      <CardContent class="space-y-3">
        <div v-if="mediaStore.uploadList.length === 0" class="rounded-2xl border border-border-subtle bg-bg-primary/40 p-4 text-sm text-text-secondary">
          {{ $t('media.noActiveUploads') }}
        </div>

        <div
          v-for="upload in mediaStore.uploadList"
          :key="upload.tempKey"
          class="rounded-2xl border border-border-subtle bg-bg-primary/50 p-4"
        >
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0 space-y-1">
              <p class="truncate text-sm font-medium text-text-display">{{ upload.file.name }}</p>
              <p class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">{{ upload.status }}</p>
              <p v-if="upload.errorDetail" class="text-xs text-text-secondary">{{ upload.errorDetail }}</p>
            </div>

            <div class="flex items-center gap-2">
              <Button
                v-if="upload.status === 'failed' || upload.status === 'conflict'"
                type="button"
                variant="outline"
                @click="retryUpload(upload.tempKey)"
              >
                {{ $t('media.retry') }}
              </Button>
              <Button type="button" variant="outline" @click="mediaStore.dismissUpload(upload.tempKey)">
                {{ $t('media.dismiss') }}
              </Button>
            </div>
          </div>

          <div class="mt-3 h-1.5 overflow-hidden rounded-full bg-border-visible">
            <div
              class="h-full rounded-full bg-text-display transition-all duration-300"
              :style="{ width: `${upload.progress}%` }"
            />
          </div>
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <CardTitle class="label-mono text-text-display text-[10px]">{{ $t('media.libraryTitle') }}</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="mb-4 flex flex-col gap-3 rounded-2xl border border-border-subtle bg-bg-primary/30 p-4 lg:flex-row lg:items-end lg:justify-between">
          <div class="grid gap-3 sm:grid-cols-2">
            <div class="space-y-1 text-xs text-text-secondary">
              <span class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ $t('media.statusFilter') }}</span>
              <select v-model="statusFilter" data-testid="filter-status" :aria-label="$t('media.statusFilter')" class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display">
                <option value="ALL">{{ $t('media.filterAll') }}</option>
                <option value="READY">READY</option>
                <option value="PROCESSING">PROCESSING</option>
                <option value="FAILED">FAILED</option>
              </select>
            </div>
            <div class="space-y-1 text-xs text-text-secondary">
              <span class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ $t('media.typeFilter') }}</span>
              <select v-model="typeFilter" data-testid="filter-type" :aria-label="$t('media.typeFilter')" class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display">
                <option value="ALL">{{ $t('media.filterAll') }}</option>
                <option value="IMAGE">{{ $t('media.typeImage') }}</option>
                <option value="VIDEO">{{ $t('media.typeVideo') }}</option>
                <option value="PDF">{{ $t('media.typePdf') }}</option>
                <option value="OTHER">{{ $t('media.typeOther') }}</option>
              </select>
            </div>
          </div>

          <p class="text-xs text-text-secondary">
            {{ visibleAssets.length }} / {{ assets.length }} {{ $t('media.filteredCountSuffix') }}
          </p>
        </div>

        <div class="mb-4 flex flex-col gap-3 rounded-2xl border border-border-subtle bg-bg-primary/20 p-4 lg:flex-row lg:items-end lg:justify-between">
          <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div class="space-y-1 text-xs text-text-secondary md:col-span-2 xl:col-span-1">
              <span class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ $t('media.searchLabel') }}</span>
              <input
                v-model="searchQuery"
                :aria-label="$t('media.searchLabel')"
                type="search"
                class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
                :placeholder="$t('media.searchPlaceholder')"
              />
            </div>
            <div class="space-y-1 text-xs text-text-secondary">
              <span class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ $t('media.sortLabel') }}</span>
              <select v-model="sortBy" data-testid="filter-sort" :aria-label="$t('media.sortLabel')" class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display">
                <option value="newest">{{ $t('media.sortNewest') }}</option>
                <option value="oldest">{{ $t('media.sortOldest') }}</option>
                <option value="filename-asc">{{ $t('media.sortFilenameAsc') }}</option>
                <option value="filename-desc">{{ $t('media.sortFilenameDesc') }}</option>
                <option value="size-desc">{{ $t('media.sortSizeDesc') }}</option>
                <option value="size-asc">{{ $t('media.sortSizeAsc') }}</option>
                <option value="status">{{ $t('media.sortStatus') }}</option>
              </select>
            </div>
            <div class="flex flex-wrap items-end gap-2">
              <Button type="button" variant="outline" @click="toggleSelectAllVisible">
                {{ allVisibleSelected ? $t('media.clearSelection') : $t('media.selectAllVisible') }}
              </Button>
              <Button type="button" variant="outline" @click="clearAssetSelection">
                {{ $t('media.clearSelection') }}
              </Button>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <span class="text-xs text-text-secondary">
              {{ selectedLibraryAssetIds.length }} {{ $t('media.selectedCountSuffix') }}
            </span>
            <AlertDialog>
              <AlertDialogTrigger as-child>
                <Button type="button" variant="outline" :disabled="selectedLibraryAssetIds.length === 0">
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
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <article
              v-for="asset in visibleAssets"
              :key="asset.assetId"
              class="relative overflow-hidden rounded-2xl border border-border-subtle bg-bg-primary/40"
            >
              <div class="flex aspect-video items-center justify-center overflow-hidden bg-bg-primary/70">
                <div class="absolute left-3 top-3 z-10 inline-flex items-center gap-2 rounded-full border border-border-visible bg-black/60 px-2 py-1 text-[10px] text-white">
                  <input
                    :checked="selectedLibraryAssetIds.includes(asset.assetId)"
                    :aria-label="$t('media.selectLabel')"
                    type="checkbox"
                    class="size-3"
                    @change="toggleAssetSelection(asset.assetId)"
                  />
                  <span>{{ $t('media.selectLabel') }}</span>
                </div>
                <img
                  v-if="isImage(asset.mediaType) && assetContentUrl(asset)"
                  :src="assetContentUrl(asset) ?? ''"
                  :alt="asset.originalFilename ?? asset.assetId"
                  class="h-full w-full object-cover"
                />
                <video
                  v-else-if="isVideo(asset.mediaType) && assetContentUrl(asset)"
                  :src="assetContentUrl(asset) ?? ''"
                  class="h-full w-full object-cover"
                  controls
                  preload="metadata"
                />
                <iframe
                  v-else-if="isPdf(asset.mediaType) && assetContentUrl(asset)"
                  :src="assetContentUrl(asset) ?? ''"
                  class="h-full w-full border-0 bg-white"
                  title="PDF preview"
                />
                <div v-else class="px-4 text-center">
                  <Video v-if="isVideo(asset.mediaType)" class="mx-auto size-8 text-text-secondary" />
                  <FileText v-else-if="isPdf(asset.mediaType)" class="mx-auto size-8 text-text-secondary" />
                  <Image v-else class="mx-auto size-8 text-text-secondary" />
                  <p class="mt-2 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary">
                    {{ asset.mediaType }}
                  </p>
                </div>
              </div>

              <div class="space-y-3 p-4">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-medium text-text-display">
                      {{ asset.originalFilename ?? $t('media.untitledAsset') }}
                    </p>
                    <p class="mt-1 truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                      {{ asset.assetId }}
                    </p>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="rounded-full border px-2 py-1 font-mono text-[9px] uppercase tracking-[0.12em]" :class="statusClass(asset.status)">
                      {{ asset.status }}
                    </span>
                    <Button
                      v-if="assetContentUrl(asset)"
                      type="button"
                      variant="outline"
                      size="icon"
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

                <div class="grid grid-cols-2 gap-3 text-xs text-text-secondary">
                  <div>
                    <p class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ $t('media.typeLabel') }}</p>
                    <p class="mt-1 truncate">{{ asset.mediaType }}</p>
                  </div>
                  <div>
                    <p class="font-mono text-[9px] uppercase tracking-[0.12em]">{{ $t('media.sizeLabel') }}</p>
                    <p class="mt-1">{{ asset.fileSizeBytes ?? '—' }}</p>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-if="mediaStore.nextCursor" class="flex justify-center">
            <Button type="button" variant="outline" @click="loadMore">
              {{ $t('media.loadMore') }}
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
