<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useWorkspaceStore } from '@modules/workspace'
import { listWorkspaceShortlinkMetrics, type WorkspaceLinkMetric } from '@modules/shortlinks'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const links = ref<WorkspaceLinkMetric[]>([])
const nextCursor = ref<string | null>(null)
const cursors = ref<Array<string | null>>([null])
const isLoading = ref(false)
const error = ref(false)
const workspaceStore = useWorkspaceStore()
let requestId = 0

async function loadPage(pageCursor: string | null) {
  const currentRequestId = ++requestId
  const requestedWorkspaceId = workspaceStore.activeWorkspaceId
  isLoading.value = true
  error.value = false
  try {
    const page = await listWorkspaceShortlinkMetrics(pageCursor)
    if (currentRequestId !== requestId || requestedWorkspaceId !== workspaceStore.activeWorkspaceId) return
    links.value = page.links
    nextCursor.value = page.nextCursor
  } catch {
    if (currentRequestId === requestId && requestedWorkspaceId === workspaceStore.activeWorkspaceId) {
      error.value = true
    }
  } finally {
    if (currentRequestId === requestId) isLoading.value = false
  }
}

async function loadNextPage() {
  if (!nextCursor.value) return
  const next = nextCursor.value
  cursors.value = [...cursors.value, next]
  await loadPage(next)
}

async function loadPreviousPage() {
  if (cursors.value.length < 2) return
  const previousCursor = cursors.value[cursors.value.length - 2] ?? null
  await loadPage(previousCursor)
  if (!error.value) cursors.value = cursors.value.slice(0, -1)
}

onMounted(() => loadPage(null))
onUnmounted(() => { requestId += 1 })
watch(() => workspaceStore.activeWorkspaceId, () => {
  cursors.value = [null]
  nextCursor.value = null
  links.value = []
  void loadPage(null)
})
</script>

<template>
  <main class="mx-auto w-full max-w-6xl space-y-6 p-6">
    <header>
      <h1 class="text-2xl font-bold text-text-display">{{ t('shortlinks.title') }}</h1>
      <p class="mt-2 text-sm text-text-secondary">{{ t('shortlinks.clicks') }}</p>
    </header>
    <p v-if="error" role="alert" class="rounded-xl border border-error/30 p-4 text-error">{{ t('shortlinks.listError') }}</p>
    <p v-else-if="isLoading" role="status" class="text-sm text-text-secondary">{{ t('common.loading') }}</p>
    <p v-else-if="links.length === 0" class="rounded-xl border border-border-visible p-4">{{ t('shortlinks.empty') }}</p>
    <div v-else class="overflow-x-auto rounded-xl border border-border-visible">
      <table class="w-full text-left text-sm">
        <thead><tr class="border-b border-border-visible"><th class="p-3">{{ t('shortlinks.shortUrl') }}</th><th class="p-3">{{ t('shortlinks.destination') }}</th><th class="p-3">{{ t('shortlinks.clicks') }}</th><th class="p-3">{{ t('shortlinks.createdAt') }}</th></tr></thead>
        <tbody><tr v-for="link in links" :key="link.id" class="border-b border-border-subtle"><td class="p-3"><a :href="link.shortUrl" class="underline" target="_blank" rel="noreferrer">{{ link.shortUrl }}</a></td><td class="max-w-sm truncate p-3">{{ link.destinationUrl }}</td><td class="p-3">{{ link.recordedRedirects }}</td><td class="p-3">{{ new Date(link.createdAt).toLocaleDateString() }}</td></tr></tbody>
      </table>
    </div>
    <nav class="flex gap-3" :aria-label="t('shortlinks.pagination')">
      <button type="button" class="rounded-full border border-border-visible px-4 py-2 text-sm disabled:opacity-50" :disabled="isLoading || cursors.length < 2" @click="loadPreviousPage">{{ t('shortlinks.previous') }}</button>
      <button type="button" class="rounded-full border border-border-visible px-4 py-2 text-sm disabled:opacity-50" :disabled="isLoading || !nextCursor" @click="loadNextPage">{{ t('shortlinks.next') }}</button>
    </nav>
  </main>
</template>
