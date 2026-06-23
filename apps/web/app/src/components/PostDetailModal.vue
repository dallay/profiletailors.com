<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarClock, ExternalLink, Trash2, X, AlertTriangle, CheckCircle2, Clock } from '@lucide/vue'
import { useFocusTrap } from '@/composables/useFocusTrap'
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
  (e: 'reschedule', payload: { id: string; scheduledAt: string }): void
}>()

const { t, locale: i18nLocale } = useI18n()
const publishingStore = usePublishingStore()

const isReadOnly = computed(() => props.publication?.status === 'PUBLISHED')
const canEdit = computed(() =>
  props.publication ? publishingStore.isPublicationEditable(props.publication.status) : false,
)
const canDelete = computed(() =>
  props.publication ? publishingStore.isPublicationDeletable(props.publication.status) : false,
)

const editTitle = ref('')
const editContent = ref('')
const editScheduledAt = ref('')
const isSaving = ref(false)
const saveError = ref('')

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
  if (urn?.startsWith('urn:li:')) {
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

const showReschedule = ref(false)
const newScheduledAt = ref('')
const rescheduleError = ref('')

const modalContainer = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(modalContainer, closeModal)

watch(
  () => [props.isOpen, props.publication] as const,
  async ([open, publication]) => {
    if (open) {
      editTitle.value = publication?.title ?? ''
      editContent.value = publication?.content ?? ''
      editScheduledAt.value = publication?.scheduledAt
        ? new Date(new Date(publication.scheduledAt).getTime() - new Date(publication.scheduledAt).getTimezoneOffset() * 60_000)
            .toISOString()
            .slice(0, 16)
        : ''
      saveError.value = ''
      deleteError.value = ''
      await nextTick()
      // Guard against the modal being closed during the await window
      if (props.isOpen) {
        activateFocusTrap()
      }
    } else {
      deactivateFocusTrap()
    }
  },
  { immediate: true },
)

function closeModal() {
  emit('close')
}

async function deletePublication() {
  if (!props.publication || isDeleting.value || !canDelete.value) return
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

async function savePublication() {
  if (!props.publication || isSaving.value || !canEdit.value) return
  isSaving.value = true
  saveError.value = ''
  try {
    await publishingStore.updatePost(props.publication.id, {
      title: editTitle.value || undefined,
      content: editContent.value,
      scheduledAt: editScheduledAt.value ? new Date(editScheduledAt.value).toISOString() : undefined,
    })
    closeModal()
  } catch (err) {
    saveError.value = err instanceof Error ? err.message : 'Failed to save post'
  } finally {
    isSaving.value = false
  }
}

function openReschedule() {
  if (!props.publication?.scheduledAt) return
  // Pre-fill with current scheduled date, local datetime-local format
  const d = new Date(props.publication.scheduledAt)
  const offset = d.getTimezoneOffset()
  const local = new Date(d.getTime() - offset * 60_000)
  newScheduledAt.value = local.toISOString().slice(0, 16)
  showReschedule.value = true
  rescheduleError.value = ''
}

async function confirmReschedule() {
  if (!props.publication || !newScheduledAt.value) return
  rescheduleError.value = ''
  const newDate = new Date(newScheduledAt.value)
  if (Number.isNaN(newDate.getTime()) || newDate <= new Date()) {
    rescheduleError.value = 'Please select a valid future date and time.'
    return
  }
  try {
    const newIso = newDate.toISOString()
    await publishingStore.reschedulePublication(props.publication.id, newIso)
    emit('reschedule', { id: props.publication.id, scheduledAt: newIso })
    showReschedule.value = false
    closeModal()
  } catch (err) {
    rescheduleError.value = err instanceof Error ? err.message : 'Failed to reschedule'
  }
}

function cancelReschedule() {
  showReschedule.value = false
  rescheduleError.value = ''
}
</script>

<template>
  <Teleport to="body">
    <!-- biome-ignore lint/a11y/noStaticElementInteractions: modal backdrop intentionally uses click only -->
    <!-- biome-ignore lint/a11y/useKeyWithClickEvents: modal backdrop intentionally uses click only -->
    <div
      v-if="isOpen && publication"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4"
      @click.self="closeModal"
    >
      <div
        ref="modalContainer"
        class="flex flex-col w-full max-w-2xl max-h-[90vh] bg-bg-surface border border-border-subtle rounded-2xl overflow-hidden shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="post-detail-title"
        @keydown.escape="closeModal"
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
              <h3 id="post-detail-title" class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
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
          <div class="space-y-1">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.titleLabel') }}
            </span>
            <input
              v-if="canEdit"
              v-model="editTitle"
              class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm text-text-display"
              :placeholder="t('postDetail.titleLabel')"
            />
            <p v-else-if="publication.title" class="text-sm font-semibold text-text-display">{{ publication.title }}</p>
          </div>

          <!-- Body text -->
          <div class="space-y-1">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.bodyLabel') }}
            </span>
            <textarea
              v-if="canEdit"
              v-model="editContent"
              class="min-h-32 w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-sm font-light leading-relaxed text-text-body"
            />
            <p v-else class="text-sm font-light leading-relaxed text-text-body whitespace-pre-wrap">
              {{ publication.content }}
            </p>
          </div>

          <div v-if="publication.thumbnail" class="space-y-2">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.mediaLabel') }}
            </span>
            <div class="overflow-hidden rounded-2xl border border-border-subtle bg-bg-primary/30">
              <img
                :src="publication.thumbnail"
                :alt="t('postDetail.mediaPreviewAlt')"
                class="block max-h-80 w-full object-cover"
              />
            </div>
          </div>

          <!-- Schedule / publish metadata -->
          <div class="grid grid-cols-2 gap-3 pt-2 border-t border-border-subtle">
            <div class="space-y-1">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.scheduledFor') }}
              </span>
              <input
                v-if="canEdit"
                v-model="editScheduledAt"
                type="datetime-local"
                class="w-full rounded-xl border border-border-visible bg-bg-surface px-3 py-2 text-xs text-text-body"
              />
              <p v-else class="text-xs text-text-body flex items-center gap-1.5">
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
          <div v-if="saveError || deleteError" class="px-6 pt-3 space-y-1">
            <p v-if="saveError" class="text-[10px] font-mono text-error">{{ saveError }}</p>
            <p v-if="deleteError" class="text-[10px] font-mono text-error">{{ deleteError }}</p>
          </div>
          <div v-if="showReschedule" class="px-6 pt-3 pb-2 space-y-2">
            <!-- biome-ignore lint/a11y/noLabelWithoutControl: t() provides accessible text, Biome can't resolve i18n keys statically -->
            <label for="reschedule-datetime" class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.scheduledFor') }}
            </label>
            <input
              id="reschedule-datetime"
              v-model="newScheduledAt"
              type="datetime-local"
              class="w-full rounded-xl border border-border-visible bg-bg-surface text-text-body px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-text-display/30"
              :aria-label="t('postDetail.scheduledFor')"
            />
            <p v-if="rescheduleError" role="alert" class="text-[10px] font-mono text-error">{{ rescheduleError }}</p>
            <div class="flex gap-2">
              <button
                @click="confirmReschedule"
                class="px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
              >
                {{ t('postDetail.rescheduleConfirm') }}
              </button>
              <button
                @click="cancelReschedule"
                class="px-3 py-2 rounded-xl border border-border-visible text-text-body hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
              >
                {{ t('postDetail.rescheduleCancel') }}
              </button>
            </div>
          </div>
          <div v-if="!showReschedule" class="flex items-center justify-between gap-3 p-6">
        <div class="flex items-center gap-2">
          <button
            v-if="canDelete"
            @click="deletePublication"
            :disabled="isDeleting"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-error hover:text-error transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <Trash2 class="size-3.5" />
            {{ t('postDetail.delete') }}
          </button>
          <button
            v-if="canEdit"
            @click="savePublication"
            :disabled="isSaving"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            {{ t('postDetail.rescheduleConfirm') }}
          </button>
          <button
            v-else-if="!isReadOnly && publication?.scheduledAt"
            @click="openReschedule"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <CalendarClock class="size-3.5" />
            {{ t('postDetail.reschedule') }}
          </button>
          <div v-else-if="isReadOnly" class="text-[10px] font-mono uppercase tracking-wider text-text-secondary">
            {{ t('postDetail.readOnlyHint') }}
          </div>
        </div>
          <div class="flex items-center gap-2">
            <button
              @click="closeModal"
              class="px-3 py-2 rounded-xl border border-border-visible text-text-body hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
            >
              {{ t('postDetail.close') }}
            </button>
            <!-- biome-ignore lint/a11y/useValidAnchor: viewPostUrl is conditionally bound, always present when this renders -->
            <a
              v-if="viewPostUrl"
              :href="viewPostUrl"
              target="_blank"
              rel="noopener noreferrer"
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
