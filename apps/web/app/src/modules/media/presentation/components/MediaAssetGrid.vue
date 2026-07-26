<script setup lang="ts">
import { Download, FileText, Image, Loader2, Trash2, Video } from '@lucide/vue'
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
import MediaAttribution from '@modules/media/presentation/components/MediaAttribution.vue'
import { useMediaAssetDisplay, formatFileSize, triggerAssetDownload } from '@modules/media/application'
import type { MediaAssetSummary } from '@modules/media/services/media-api'

const _props = defineProps<{
  assets: MediaAssetSummary[]
  selectedAssetIds: string[]
  isLoading: boolean
  loadError: string | null
  hasNextPage: boolean
  hasActiveFilters: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-asset', assetId: string): void
  (e: 'delete-asset', assetId: string): void
  (e: 'load-more'): void
}>()

function getAssetDisplay(asset: MediaAssetSummary) {
  return useMediaAssetDisplay(asset)
}

function handleDownload(asset: MediaAssetSummary) {
  const { downloadUrl } = useMediaAssetDisplay(asset)
  triggerAssetDownload(downloadUrl.value, asset.originalFilename ?? 'media-asset')
}
</script>

<template>
  <div class="space-y-5">
    <p v-if="loadError" class="rounded-xl border border-error/30 bg-error/10 p-3 text-sm text-error">
      {{ loadError }}
    </p>

    <div v-if="isLoading && assets.length === 0" class="flex items-center gap-2 text-sm text-text-secondary">
      <Loader2 class="size-4 animate-spin" />
      {{ $t('media.loading') }}
    </div>

    <div v-else-if="assets.length === 0" class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/30 p-10 text-center">
      <Image class="mx-auto size-8 text-text-secondary" />
      <p class="mt-3 text-sm text-text-display">
        {{ $t(hasActiveFilters ? 'media.noFilteredAssetsTitle' : 'media.emptyTitle') }}
      </p>
      <p class="mt-1 text-xs text-text-secondary">
        {{ $t(hasActiveFilters ? 'media.noFilteredAssetsBody' : 'media.emptyBody') }}
      </p>
    </div>

    <div v-else class="space-y-5">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <article
          v-for="asset in assets"
          :key="asset.assetId"
          class="group relative overflow-hidden rounded-2xl border border-border-subtle bg-bg-primary/40 transition-colors hover:border-text-display/30"
        >
          <div class="relative aspect-square overflow-hidden bg-bg-primary/70">
            <!-- Selection checkbox -->
            <div class="absolute left-3 top-3 z-20 opacity-0 transition-opacity group-hover:opacity-100">
              <input
                :checked="selectedAssetIds.includes(asset.assetId)"
                :aria-label="$t('media.selectLabel')"
                type="checkbox"
                class="size-4 rounded border-border-visible bg-black/60 text-primary focus:ring-primary"
                @change="emit('toggle-asset', asset.assetId)"
              />
            </div>

            <!-- Status badge -->
            <span
              data-testid="status-badge"
              class="absolute right-3 top-3 z-10 rounded-full px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em]"
              :class="getAssetDisplay(asset).statusClass.value"
            >
              {{ asset.status }}
            </span>

            <!-- Hover action buttons -->
            <div class="absolute bottom-3 right-3 z-20 flex items-center gap-2 opacity-0 transition-opacity group-hover:opacity-100">
              <Button
                v-if="getAssetDisplay(asset).downloadUrl.value"
                type="button"
                variant="outline"
                size="icon"
                class="size-8 bg-black/60 text-white hover:bg-black/80"
                @click="handleDownload(asset)"
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
                    <AlertDialogDescription>{{ $t('media.deleteConfirmBody') }}</AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>{{ $t('workspace.cancel') }}</AlertDialogCancel>
                    <AlertDialogAction @click="emit('delete-asset', asset.assetId)">
                      {{ $t('media.deleteAction') }}
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>

            <!-- Media preview -->
            <img
              v-if="getAssetDisplay(asset).isImage.value && getAssetDisplay(asset).previewUrl.value"
              :src="getAssetDisplay(asset).previewUrl.value ?? ''"
              :alt="asset.originalFilename ?? asset.assetId"
              class="h-full w-full object-cover"
            />
            <video
              v-else-if="getAssetDisplay(asset).isVideo.value && getAssetDisplay(asset).previewUrl.value"
              :src="getAssetDisplay(asset).previewUrl.value ?? ''"
              class="h-full w-full object-cover"
              controls
              preload="metadata"
            >
              <track kind="captions" src="" label="No captions available">
            </video>
            <iframe
              v-else-if="getAssetDisplay(asset).isPdf.value && getAssetDisplay(asset).previewUrl.value"
              :src="getAssetDisplay(asset).previewUrl.value ?? ''"
              class="h-full w-full border-0 bg-white"
              title="PDF preview"
            />
            <div v-else class="flex h-full items-center justify-center px-4">
              <div class="text-center">
                <Video v-if="getAssetDisplay(asset).isVideo.value" class="mx-auto size-8 text-text-secondary" />
                <FileText v-else-if="getAssetDisplay(asset).isPdf.value" class="mx-auto size-8 text-text-secondary" />
                <Image v-else class="mx-auto size-8 text-text-secondary" />
                <p class="mt-2 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary">
                  {{ asset.mediaType }}
                </p>
              </div>
            </div>
          </div>

          <!-- Metadata footer -->
          <div class="space-y-1 p-3">
            <p class="truncate text-sm font-medium text-text-display">
              {{ asset.originalFilename ?? $t('media.untitledAsset') }}
            </p>
            <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
              {{ formatFileSize(asset.fileSizeBytes) ?? '—' }}
            </p>
          </div>
          <MediaAttribution
            :author-name="asset.authorName"
            :author-url="asset.authorUrl"
            :source-provider="asset.sourceProvider"
            :licence="asset.licence"
          />
        </article>
      </div>

      <div v-if="hasNextPage" class="flex justify-center">
        <Button type="button" variant="outline" @click="emit('load-more')">
          {{ $t('media.loadMore') }}
        </Button>
      </div>
    </div>
  </div>
</template>
