<script setup lang="ts">
import { Trash2 } from '@lucide/vue'
import { usePublishingStore, type Publication } from '@modules/publishing/infrastructure/publishing.store'
import ConflictBadge from '@modules/publishing/presentation/components/ConflictBadge.vue'

const _props = defineProps<{
  publications: Publication[]
}>()

const emit = defineEmits<{
  (e: 'click-publication', pub: Publication): void
  (e: 'delete-publication', id: string): void
  (e: 'reconnect'): void
}>()

const publishingStore = usePublishingStore()
</script>

<template>
  <div class="flex h-full min-h-0 flex-col gap-4">
    <div
      v-if="publications.length === 0"
      class="border border-dashed border-border-visible rounded-2xl p-12 text-center text-text-secondary font-mono text-xs uppercase tracking-wider"
    >
      {{ $t('dashboard.noPosts') || 'No posts match your current filters.' }}
    </div>

    <div v-else class="thin-scrollbar min-h-0 flex-1 overflow-y-auto space-y-3 pr-1">
      <button
        v-for="pub in publications"
        :key="pub.id"
        type="button"
        class="group/card flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl border border-border-subtle bg-bg-surface hover:border-text-secondary transition-all cursor-pointer w-full text-left"
        @click="emit('click-publication', pub)"
        @keydown.enter.self.stop.prevent="emit('click-publication', pub)"
        @keydown.space.self.stop.prevent="emit('click-publication', pub)"
      >
        <div class="space-y-2 flex-1 min-w-0">
          <div class="flex items-center gap-3">
            <span class="font-mono text-[9px] uppercase tracking-widest text-text-secondary bg-bg-primary border border-border-visible px-2 py-0.5 rounded-md">
              {{ new Date(pub.scheduledAt).toLocaleString() }}
            </span>
            <span
              class="font-mono text-[9px] uppercase tracking-widest px-2 py-0.5 rounded-md font-bold"
              :class="{
                'bg-success/10 text-success border border-success/20': pub.status === 'PUBLISHED',
                'bg-text-display/10 text-text-display border border-border-visible': pub.status === 'QUEUED',
                'bg-warning/10 text-warning border border-warning/20': pub.status === 'BLOCKED',
                'bg-error/10 text-error border border-error/20': pub.status === 'FAILED',
              }"
            >
              {{ pub.status }}
            </span>
            <!-- biome-ignore lint/a11y/useSemanticElements: parent is <button>, cannot nest HTML buttons -->
            <span
              v-if="pub.status === 'BLOCKED'"
              role="button"
              tabindex="0"
              class="text-[9px] underline text-warning hover:text-warning/80 font-medium cursor-pointer"
              @click.stop="emit('reconnect')"
              @keydown.enter.stop="emit('reconnect')"
              @keydown.space.stop="emit('reconnect')"
            >
              Reconnect
            </span>
            <ConflictBadge v-if="pub.hasConflict" variant="inline" />
          </div>
          <p class="text-sm font-light text-text-body leading-relaxed break-words">
            {{ pub.content }}
          </p>
          <div v-if="pub.thumbnail" class="h-24 w-full overflow-hidden rounded-xl border border-border-subtle">
            <img :src="pub.thumbnail" class="h-full w-full object-cover" alt="" />
          </div>
        </div>

        <div class="flex items-center gap-3 shrink-0">
          <div class="flex gap-1.5">
            <span
              v-for="ch in pub.channels"
              :key="ch"
              class="border border-border-visible bg-bg-primary px-2.5 py-0.5 rounded-full font-mono text-[9px] tracking-wider text-text-secondary uppercase"
            >
              {{ ch }}
            </span>
          </div>

          <!-- biome-ignore lint/a11y/useSemanticElements: parent is <button>, cannot nest HTML buttons -->
          <span
            v-if="publishingStore.isPublicationDeletable(pub.status)"
            role="button"
            tabindex="0"
            class="group-hover/card:opacity-100 opacity-0 size-8 flex items-center justify-center rounded-xl border border-border-visible hover:border-error text-text-secondary hover:text-error transition-all bg-bg-primary cursor-pointer"
            title="Delete publication"
            @click.stop="emit('delete-publication', pub.id)"
            @keydown.enter.stop="emit('delete-publication', pub.id)"
            @keydown.space.stop="emit('delete-publication', pub.id)"
          >
            <Trash2 class="size-4" />
          </span>
        </div>
      </button>
    </div>
  </div>
</template>
