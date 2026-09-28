<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Sheet, SheetClose, SheetContent, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import type { SchedulerStatus } from '@modules/publishing/application/useCalendarUrl'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'

type FilterState = {
  status: SchedulerStatus
  timezone: string
  channelIds: string[]
  q: string
}
const props = defineProps<{
  open: boolean
  status: SchedulerStatus
  timezone: string
  channelIds: string[]
  q: string
  filtersCount: number
}>()
const emit = defineEmits<{
  (event: 'update:open', value: boolean): void
  (event: 'change:filter', filter: Partial<FilterState>): void
}>()
const publishingStore = usePublishingStore()
function resolveBrowserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
const BASE_TIMEZONES = ['Europe/Madrid', 'UTC', 'America/New_York']
const timezoneOptions = computed(() => {
  const active = resolveBrowserTimezone()
  return active && !BASE_TIMEZONES.includes(active) ? [active, ...BASE_TIMEZONES] : BASE_TIMEZONES
})
const draft = ref<FilterState>({
  status: props.status,
  timezone: props.timezone,
  channelIds: [...props.channelIds],
  q: props.q,
})
watch(() => [props.status, props.timezone, props.channelIds, props.q, props.open], () => {
  draft.value = {
    status: props.status,
    timezone: props.timezone,
    channelIds: [...props.channelIds],
    q: props.q,
  }
})
function updateChannel(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) return
  draft.value.channelIds = target.value ? [target.value] : []
}
function updateQuery(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLInputElement)) return
  draft.value.q = target.value
}
function apply() {
  emit('change:filter', draft.value)
  emit('update:open', false)
}
function reset() {
  draft.value = { status: 'all', timezone: resolveBrowserTimezone(), channelIds: [], q: '' }
  emit('change:filter', draft.value)
  emit('update:open', false)
}
</script>
<template>
  <Sheet :open="open" @update:open="emit('update:open', $event)">
    <SheetContent data-testid="mobile-filters-sheet" side="bottom" class="rounded-t-2xl">
      <SheetHeader>
        <SheetTitle>{{ $t('scheduler.filters') }}</SheetTitle>
      </SheetHeader>
      <div class="grid gap-4 px-4">
        <label class="grid gap-2 font-mono text-xs text-text-secondary">
          {{ $t('scheduler.channelsLabel') }}
          <select class="min-h-11 rounded-lg border border-border-visible bg-bg-primary px-3 text-sm text-text-display" :value="draft.channelIds[0] ?? ''" @change="updateChannel">
            <option value="">{{ $t('scheduler.allChannels') }}</option>
            <option v-for="channel in publishingStore.channels" :key="channel.accountId" :value="channel.accountId">
              {{ channel.provider }} ({{ channel.handle }})
            </option>
          </select>
        </label>
        <label class="grid gap-2 font-mono text-xs text-text-secondary">
          {{ $t('scheduler.allPosts') }}
          <select v-model="draft.status" class="min-h-11 rounded-lg border border-border-visible bg-bg-primary px-3 text-sm text-text-display">
            <option value="all">{{ $t('scheduler.allPosts') }}</option>
            <option value="queued">{{ $t('scheduler.statusQueued') }}</option>
            <option value="published">{{ $t('scheduler.statusPublished') }}</option>
            <option value="cancelled">{{ $t('scheduler.statusCancelled') }}</option>
          </select>
        </label>
        <label class="grid gap-2 font-mono text-xs text-text-secondary">
          {{ $t('scheduler.timezoneLabel') }}
          <select v-model="draft.timezone" class="min-h-11 rounded-lg border border-border-visible bg-bg-primary px-3 text-sm text-text-display">
            <option v-for="zone in timezoneOptions" :key="zone" :value="zone">{{ zone }}</option>
          </select>
        </label>
        <label class="grid gap-2 font-mono text-xs text-text-secondary">
          {{ $t('scheduler.search') }}
          <input
            type="search"
            class="min-h-11 rounded-lg border border-border-visible bg-bg-primary px-3 text-sm text-text-display"
            :value="draft.q"
            @input="updateQuery"
          />
        </label>
      </div>
      <SheetFooter class="flex-row justify-between px-4">
        <Button type="button" variant="ghost" @click="reset">{{ $t('scheduler.reset') }}</Button>
        <SheetClose as-child>
          <Button type="button" @click="apply">{{ $t('scheduler.apply') }}</Button>
        </SheetClose>
      </SheetFooter>
    </SheetContent>
  </Sheet>
</template>
