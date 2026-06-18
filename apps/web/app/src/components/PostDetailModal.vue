<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarClock, ExternalLink, Trash2, X, AlertTriangle, CheckCircle2, Clock } from '@lucide/vue'
import { usePublishingStore, type Publication } from '@/stores/publishing'
import { getProviderColor, getProviderBadge } from '@/lib/provider-styles'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
    publication: Publication | null
  }>(),
  { isOpen: false },
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'deleted', id: string): void
}>()

const { t, locale: i18nLocale } = useI18n()
const publishingStore = usePublishingStore()

const isReadOnly = computed(() => props.publication?.status === 'PUBLISHED')

const statusLabel = computed(() => {
  if (!props.publication) return ''
  switch (props.publication.status) {
    case 'DRAFT':
      return t('postDetail.status.draft')
    case 'QUEUED':
      return t('postDetail.status.queued')
    case 'SCHEDULED':
      return t('postDetail.status.scheduled')
    case 'PROCESSING':
      return t('postDetail.status.processing')
    case 'PUBLISHED':
      return t('postDetail.status.published')
    case 'BLOCKED':
      return t('postDetail.status.blocked')
    case 'FAILED':
      return t('postDetail.status.failed')
    case 'CANCELLED':
      return t('postDetail.status.cancelled')
    default:
      return props.publication.status
  }
})

const statusTone = computed(() => {
  if (!props.publication) return 'text-text-secondary'
  switch (props.publication.status) {
    case 'PUBLISHED':
      return 'text-success'
    case 'BLOCKED':
    case 'FAILED':
      return 'text-error'
    case 'CANCELLED':
      return 'text-text-secondary'
    case 'PROCESSING':
    case 'QUEUED':
    case 'SCHEDULED':
      return 'text-warning'
    default:
      return 'text-text-secondary'
  }
})

const viewPostUrl = computed(() => {
  if (!props.publication) return null
  // Prefer the public URL the backend provides (e.g. https://www.linkedin.com/feed/update/...)
  if (props.publication.publicUrl) return props.publication.publicUrl
  // Fallback: build a LinkedIn share URL from the URN when the backend did not return one
  const urn = props.publication.externalPublicationId
  if (urn && urn.startsWith('urn:li:')) {
    return `https://www.linkedin.com/feed/update/${encodeURIComponent(urn)}`
  }
  return null
})

const dateLocale = computed(() => (i18nLocale.value === 'es' ? 'es-ES' : 'en-US'))

const scheduledAtLabel = computed(() => {
  if (!props.publication?.scheduledAt) return ''
  const d = new Date(props.publication.scheduledAt)
  return d.toLocaleString(dateLocale.value, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const publishedAtLabel = computed(() => {
  if (!props.publication?.publishedAt) return ''
  const d = new Date(props.publication.publishedAt)
  return d.toLocaleString(dateLocale.value, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const isDeleting = ref(false)
const deleteError = ref('')

function closeModal() {
  emit('close')
}

function openPostInNewTab() {
  const url = viewPostUrl.value
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function deletePublication() {
  if (!props.publication || isDeleting.value) return
  isDeleting.value = true
  deleteError.value = ''
  try {
    await publishingStore.deletePost(props.publication.id)
    emit('deleted', props.publication.id)
    closeModal()
  } catch (err) {
    deleteError.value = err instanceof Error ? err.message : 'Failed to delete post'
    console.error('Failed to delete publication', err)
  } finally {
    isDeleting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen && publication"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4"
      @click.self="closeModal"
    >
      <div
        class="flex flex-col w-full max-w-2xl max-h-[90vh] bg-bg-surface border border-border-subtle rounded-2xl overflow-hidden shadow-2xl"
        role="dialog"
        :aria-label="t('postDetail.title')"
      >
        <!-- Header -->
        <header class="flex items-center justify-between p-6 border-b border-border-subtle">
          <div class="flex items-center gap-3">
            <span
              class="flex size-9 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-xs font-bold uppercase text-text-display"
            >
              {{ getProviderBadge(publication.channels[0] || 'linkedin') }}
            </span>
            <div class="space-y-0.5">
              <h3 class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
                {{ t('postDetail.title') }}
              </h3>
              <div class="flex items-center gap-2 text-[10px] font-mono uppercase tracking-wider">
                <span :class="statusTone">
                  <span v-if="publication.status === 'PUBLISHED'" class="inline-flex items-center gap-1">
                    <CheckCircle2 class="size-3" />
                    {{ statusLabel }}
                  </span>
                  <span v-else-if="publication.status === 'BLOCKED' || publication.status === 'FAILED'" class="inline-flex items-center gap-1">
                    <AlertTriangle class="size-3" />
                    {{ statusLabel }}
                  </span>
                  <span v-else class="inline-flex items-center gap-1">
                    <Clock class="size-3" />
                    {{ statusLabel }}
                  </span>
                </span>
                <span v-if="isReadOnly" class="px-1.5 py-0.5 rounded bg-text-secondary/15 text-text-secondary">
                  {{ t('postDetail.readOnly') }}
                </span>
              </div>
            </div>
          </div>
          <button
            @click="closeModal"
            class="flex size-8 items-center justify-center rounded-xl border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display cursor-pointer"
            :aria-label="t('postDetail.close')"
          >
            <X class="size-4" />
          </button>
        </header>

        <!-- Body -->
        <div class="flex-1 overflow-y-auto p-6 space-y-5">
          <!-- Title -->
          <div v-if="publication.title" class="space-y-1">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.titleLabel') }}
            </span>
            <p class="text-sm font-semibold text-text-display">{{ publication.title }}</p>
          </div>

          <!-- Body text -->
          <div class="space-y-1">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.bodyLabel') }}
            </span>
            <p class="text-sm font-light leading-relaxed text-text-body whitespace-pre-wrap">
              {{ publication.content }}
            </p>
          </div>

          <!-- Schedule / publish metadata -->
          <div class="grid grid-cols-2 gap-3 pt-2 border-t border-border-subtle">
            <div class="space-y-1">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.scheduledFor') }}
              </span>
              <p class="text-xs text-text-body flex items-center gap-1.5">
                <CalendarClock class="size-3" />
                {{ scheduledAtLabel }}
              </p>
            </div>
            <div v-if="publication.publishedAt" class="space-y-1">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.publishedAt') }}
              </span>
              <p class="text-xs text-text-body flex items-center gap-1.5">
                <CheckCircle2 class="size-3 text-success" />
                {{ publishedAtLabel }}
              </p>
            </div>
            <div v-if="publication.externalPublicationId" class="space-y-1 col-span-2">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.externalId') }}
              </span>
              <p class="text-xs text-text-body font-mono break-all">
                {{ publication.externalPublicationId }}
              </p>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <footer class="border-t border-border-subtle bg-bg-primary/40">
          <div v-if="deleteError" class="px-6 pt-3">
            <p class="text-[10px] font-mono text-error">{{ deleteError }}</p>
          </div>
          <div class="flex items-center justify-between gap-3 p-6">
          <button
            v-if="!isReadOnly"
            @click="deletePublication"
            :disabled="isDeleting"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-error hover:text-error transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <Trash2 class="size-3.5" />
            {{ t('postDetail.delete') }}
          </button>
          <div v-else class="text-[10px] font-mono uppercase tracking-wider text-text-secondary">
            {{ t('postDetail.readOnlyHint') }}
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="closeModal"
              class="px-3 py-2 rounded-xl border border-border-visible text-text-body hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
            >
              {{ t('postDetail.close') }}
            </button>
            <a
              v-if="viewPostUrl"
              :href="viewPostUrl"
              target="_blank"
              rel="noopener noreferrer"
              @click.stop
              class="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
            >
              <ExternalLink class="size-3.5" />
              {{ t('postDetail.viewPost') }}
            </a>
            <button
              v-else
              disabled
              class="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display/40 text-bg-primary/60 cursor-not-allowed text-xs font-mono uppercase tracking-wider font-bold"
              :title="t('postDetail.viewPostUnavailable')"
            >
              <ExternalLink class="size-3.5" />
              {{ t('postDetail.viewPost') }}
            </button>
          </div>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
