<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Plus, Trash2 } from '@lucide/vue'
import { Card } from '@/components/ui/card'
import { usePublishingStore, type Publication } from '@modules/publishing/infrastructure/publishing.store'
import { useSchedulerWeekTimeline } from '@modules/publishing/application'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import ConflictBadge from '@modules/publishing/presentation/components/ConflictBadge.vue'
import { getProviderColor } from '@shared/lib/provider-styles'

const props = defineProps<{
  weekDays: Date[]
  hourSlots: { label: string; hour: number }[]
  publicationsBySlot: Map<string, Publication[]>
}>()

const emit = defineEmits<{
  (e: 'click-publication', pub: Publication): void
  (e: 'add-post', payload: { date: Date; hour: number }): void
  (e: 'delete-publication', id: string): void
  (e: 'dragstart', payload: { event: DragEvent; pub: Publication }): void
  (e: 'dragend', event: DragEvent): void
  (e: 'drop-cell', payload: { event: DragEvent; date: Date; hour: number }): void
}>()

const { t } = useI18n()
const publishingStore = usePublishingStore()

const { dateKey, slotKey, formatDayName, isToday, isPastSlot } = useSchedulerWeekTimeline()

function publicationsForSlot(date: Date, hour: number): Publication[] {
  return props.publicationsBySlot.get(slotKey(date, hour)) ?? []
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col">
    <Card class="flex min-h-0 flex-1 flex-col overflow-hidden border border-border-subtle bg-bg-surface p-0">
      <!-- Day column headers -->
      <div class="shrink-0 grid grid-cols-[48px_repeat(7,minmax(0,1fr))] border-b border-border-subtle bg-bg-primary">
        <div class="py-3.5 border-r border-border-subtle" />
        <div
          v-for="day in weekDays"
          :key="day.toISOString()"
          class="py-3.5 text-center border-r border-border-subtle last:border-r-0 flex flex-col gap-0.5"
          :class="{ 'bg-bg-surface/50': isToday(day) }"
        >
          <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
            {{ formatDayName(day).substring(0, 3) }}
          </span>
          <span
            class="font-mono text-xs font-bold leading-none size-6 flex items-center justify-center mx-auto rounded-full"
            :class="isToday(day) ? 'bg-text-display text-bg-primary' : 'text-text-display'"
          >
            {{ day.getDate() }}
          </span>
        </div>
      </div>

      <!-- Hour-slot rows -->
      <div data-testid="week-timeline-viewport" class="thin-scrollbar relative min-h-0 flex-1 overflow-y-auto">
        <div
          v-for="slot in hourSlots"
          :key="slot.hour"
          class="grid h-[96px] grid-cols-[48px_repeat(7,minmax(0,1fr))] border-b border-border-subtle last:border-b-0"
        >
          <!-- Hour label -->
          <div class="py-2 border-r border-border-subtle flex items-start justify-center">
            <span class="font-mono text-[9px] tracking-wider text-text-secondary">
              {{ slot.label }}
            </span>
          </div>

          <!-- Day cells -->
          <button
            v-for="day in weekDays"
            :key="day.toISOString()"
            type="button"
            :disabled="isPastSlot(day, slot.hour)"
            class="relative p-2 border-r border-border-subtle last:border-r-0 transition-all group/cell flex flex-col justify-start gap-1 select-none overflow-hidden"
            :class="isPastSlot(day, slot.hour)
              ? 'bg-text-secondary/5 text-text-secondary cursor-not-allowed after:absolute after:inset-0 after:bg-[repeating-linear-gradient(-45deg,transparent,transparent_10px,var(--border-color)_10px,var(--border-color)_11px)] after:opacity-10 after:z-0'
              : 'hover:bg-bg-primary/20 cursor-pointer'"
            :aria-disabled="isPastSlot(day, slot.hour)"
            :title="isPastSlot(day, slot.hour) ? 'Past time slots are disabled (read-only)' : undefined"
            @click="isPastSlot(day, slot.hour) ? undefined : emit('add-post', { date: day, hour: slot.hour })"
            @keydown.enter.prevent="isPastSlot(day, slot.hour) ? undefined : emit('add-post', { date: day, hour: slot.hour })"
            @keydown.space.prevent="isPastSlot(day, slot.hour) ? undefined : emit('add-post', { date: day, hour: slot.hour })"
            @dragover.prevent="!isPastSlot(day, slot.hour)"
            @drop.prevent="!isPastSlot(day, slot.hour) ? emit('drop-cell', { event: $event, date: day, hour: slot.hour }) : undefined"
          >
            <template v-for="slotPubs in [publicationsForSlot(day, slot.hour)]" :key="slotPubs.length">
              <!-- biome-ignore lint/a11y/noStaticElementInteractions: non-button container required to avoid nested buttons (delete btn inside card) -->
              <div
                v-for="pub in slotPubs.slice(0, 2)"
                :key="pub.id"
                :draggable="true"
                class="relative z-10 grid w-full min-w-0 overflow-hidden rounded-md border bg-bg-surface text-left shadow-sm transition-[box-shadow,transform] group/card cursor-pointer hover:-translate-y-px hover:shadow-md"
                :class="[
                  getProviderColor(pub.channels[0] || 'linkedin'),
                  slotPubs.length > 1
                    ? 'h-[36px] grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-1.5 px-2 py-1'
                    : 'h-[72px] grid-cols-[minmax(0,1fr)_auto] grid-rows-[auto_1fr] gap-x-2 px-2 py-1.5',
                ]"
                @click.stop="emit('click-publication', pub)"
                @keydown.enter.self.stop.prevent="emit('click-publication', pub)"
                @keydown.space.self.stop.prevent="emit('click-publication', pub)"
                @dragstart="emit('dragstart', { event: $event, pub })"
                @dragend="emit('dragend', $event)"
              >
                <div
                  class="flex min-w-0 items-center gap-1.5"
                  :class="slotPubs.length > 1 ? '' : 'col-start-1 row-start-1'"
                >
                  <span
                    v-for="channel in pub.channels"
                    :key="channel"
                    class="flex size-3 shrink-0 items-center justify-center rounded-[3px]"
                  >
                    <SocialProviderIcon :provider="channel" />
                  </span>
                  <span class="shrink-0 font-mono text-[8px] font-bold tracking-wider opacity-80 uppercase">
                    {{ new Date(pub.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                  </span>
                  <p
                    v-if="slotPubs.length > 1"
                    class="min-w-0 truncate text-[10px] font-medium leading-tight text-text-body"
                  >
                    {{ pub.content }}
                  </p>
                </div>

                <p
                  v-if="slotPubs.length === 1"
                  class="col-start-1 row-start-2 min-w-0 overflow-hidden text-[11px] font-light leading-snug text-text-body break-words [display:-webkit-box] [-webkit-box-orient:vertical] [-webkit-line-clamp:2] [overflow-wrap:anywhere]"
                >
                  {{ pub.content }}
                </p>

                <div
                  class="flex shrink-0 items-center justify-end gap-1"
                  :class="slotPubs.length > 1 ? 'col-start-3' : 'col-start-2 row-span-2 row-start-1 self-stretch'"
                >
                  <span
                    v-if="pub.status === 'BLOCKED'"
                    class="rounded-sm border border-warning/30 bg-warning/20 px-1 py-0.5 text-[7px] font-bold tracking-wider text-warning uppercase"
                  >
                    BLOCKED
                  </span>
                  <ConflictBadge v-if="pub.hasConflict" variant="badge" />
                  <div
                    v-if="pub.thumbnail"
                    class="overflow-hidden rounded-sm border border-border-subtle/80"
                    :class="slotPubs.length > 1 ? 'size-6' : 'h-full w-14'"
                  >
                    <img :src="pub.thumbnail" class="h-full w-full object-cover" alt="" />
                  </div>
                </div>

                <!-- Delete button overlay -->
                <button
                  v-if="publishingStore.isPublicationDeletable(pub.status)"
                  type="button"
                  class="absolute top-1 right-1 opacity-0 group-hover/card:opacity-100 size-5 flex items-center justify-center rounded-full bg-black/60 text-white hover:bg-error transition-all"
                  title="Delete publication"
                  @click.stop="emit('delete-publication', pub.id)"
                >
                  <Trash2 class="size-2.5" />
                </button>
              </div>

              <!-- "+N more" indicator -->
              <div
                v-if="slotPubs.length > 2"
                class="text-[7px] font-mono text-text-secondary pl-1"
              >
                {{ t('scheduler.morePosts', { count: slotPubs.length - 2 }) }}
              </div>

              <!-- Add post button -->
              <button
                v-if="!isPastSlot(day, slot.hour)"
                type="button"
                class="hidden group-hover/cell:flex items-center justify-center size-6 rounded-lg border border-dashed border-text-secondary/30 text-text-secondary/50 hover:border-text-display/40 hover:text-text-display/60 hover:bg-bg-primary/30 transition-all mt-auto cursor-pointer"
                :title="$t('scheduler.addPost')"
                @click.stop="emit('add-post', { date: day, hour: slot.hour })"
              >
                <Plus class="size-3" />
              </button>
            </template>
          </button>
        </div>
      </div>
    </Card>
  </div>
</template>
