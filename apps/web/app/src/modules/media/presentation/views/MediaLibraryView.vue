<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RefreshCw, UploadCloud } from '@lucide/vue'
import { useMediaStore } from '@modules/media/infrastructure/media.store'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { Button } from '@/components/ui/button'
import { useMediaLibraryFilters } from '@modules/media/application/useMediaLibraryFilters'
import { useMediaLibrarySelection } from '@modules/media/application/useMediaLibrarySelection'
import MediaFilterBar from '@modules/media/presentation/components/MediaFilterBar.vue'
import MediaBulkActionBar from '@modules/media/presentation/components/MediaBulkActionBar.vue'
import MediaAssetGrid from '@modules/media/presentation/components/MediaAssetGrid.vue'

const mediaStore = useMediaStore()
const authStore = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)
const isRefreshing = ref(false)
const uploadRequiresVerification = ref(false)

const assets = computed(() =>
  mediaStore.assetIds
    .map((id) => mediaStore.assetsById[id])
    .filter((asset) => asset !== undefined),
)

// Use composables for filters and selection
const filters = useMediaLibraryFilters(assets)
const selection = useMediaLibrarySelection(filters.visibleAssets)

const readyAssets = computed(() => assets.value.filter((a) => a.status === 'READY'))
const processingAssets = computed(() => assets.value.filter((a) => a.status === 'PENDING_UPLOAD' || a.status === 'UPLOADING'))
const failedAssets = computed(() => assets.value.filter((a) => a.status === 'FAILED'))
const canUploadMedia = computed(() => authStore.isEmailVerified && !uploadRequiresVerification.value)
const showVerificationGuidance = computed(() => !canUploadMedia.value)

async function deletePersistedAsset(assetId: string) {
  try {
    await mediaStore.deletePersistedAsset(assetId)
    selection.removeSelectedAsset(assetId)
  } catch {
    // Error state is tracked in mediaStore; selection is preserved on failure
  }
}

async function deleteSelectedAssets() {
  for (const assetId of selection.selectedAssetIds.value) {
    try {
      await mediaStore.deletePersistedAsset(assetId)
    } catch {
      // Continue deleting other selected assets even if one fails
    }
  }
  selection.clearSelection()
}

async function refreshLibrary() {
  isRefreshing.value = true
  try {
    await mediaStore.loadAssets('READY,PENDING_UPLOAD,UPLOADING,FAILED,SUSPENDED')
  } finally {
    isRefreshing.value = false
  }
}

async function loadMore() {
  await mediaStore.loadNextPage('READY,PENDING_UPLOAD,UPLOADING,FAILED,SUSPENDED')
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
    }
  }
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files?.length) return
  await uploadFiles(Array.from(target.files))
  target.value = ''
}

watch(
  () => authStore.isEmailVerified,
  (isVerified) => { if (isVerified) uploadRequiresVerification.value = false },
)

onMounted(async () => {
  await refreshLibrary()
})
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-8">
    <!-- Page header -->
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div class="space-y-2">
        <h2 class="text-3xl font-light tracking-tight text-text-display">{{ $t('nav.media') }}</h2>
        <p class="text-sm text-text-secondary">{{ $t('media.subtitle') }}</p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label for="media-library-file-input" class="sr-only">Upload media files</label>
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

    <!-- Email verification guidance -->
    <div
      v-if="showVerificationGuidance"
      data-testid="media-verification-guidance"
      role="alert"
      class="rounded-2xl border border-warning/40 bg-warning/10 px-5 py-4 text-sm text-text-body"
    >
      <p class="font-medium text-text-display">{{ $t('media.verificationRequired') }}</p>
      <p class="mt-1 text-text-secondary">{{ $t('media.verificationGuidance') }}</p>
    </div>

    <!-- Stats + filters -->
    <MediaFilterBar
      v-model:search-query="filters.searchQuery"
      v-model:status-filter="filters.statusFilter"
      v-model:type-filter="filters.typeFilter"
      v-model:sort-by="filters.sortBy"
      :all-visible-selected="selection.allVisibleSelected"
      :visible-count="filters.visibleAssets.length"
      :total-count="assets.length"
      :ready-count="readyAssets.length"
      :processing-count="processingAssets.length + mediaStore.pendingUploads.length"
      :failed-count="failedAssets.length + mediaStore.failedUploads.length"
      @toggle-select-all="selection.toggleSelectAllVisible"
    />

    <!-- Bulk selection actions -->
    <MediaBulkActionBar
      v-if="selection.selectedAssetIds.length > 0"
      :selected-count="selection.selectedAssetIds.length"
      @clear-selection="selection.clearSelection"
      @delete-selected="deleteSelectedAssets"
    />

    <!-- Asset grid -->
    <MediaAssetGrid
      :assets="filters.visibleAssets"
      :selected-asset-ids="selection.selectedAssetIds"
      :is-loading="mediaStore.isLoading"
      :load-error="mediaStore.loadError"
      :has-next-page="!!mediaStore.nextCursor"
      @toggle-asset="selection.toggleAssetSelection"
      @delete-asset="deletePersistedAsset"
      @load-more="loadMore"
    />
  </div>
</template>
