<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronsUpDown, Plus } from '@lucide/vue'
import WorkspaceAvatar from '@/components/WorkspaceAvatar.vue'
import { usePopoverDismissal } from '@/composables/usePopoverDismissal'
import type { WorkspaceSummary } from '@/lib/auth-api'

const props = defineProps<{
  activeWorkspace: WorkspaceSummary | null
  options: WorkspaceSummary[]
  isLoading: boolean
}>()

const emit = defineEmits<(e: 'select', workspace: WorkspaceSummary) => void>()

const containerRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)

const { open, toggle, close } = usePopoverDismissal({
  container: containerRef,
  trigger: triggerRef,
})

function selectWorkspace(ws: WorkspaceSummary) {
  emit('select', ws)
  close()
}

const isEmpty = computed(() => props.options.length === 0 && !props.isLoading)
</script>

<template>
  <div
    ref="containerRef"
    class="relative"
  >
    <div
      v-if="open"
      id="sidebar-workspace-menu"
      class="absolute top-0 left-0 z-50 w-full rounded-2xl border border-border-subtle bg-bg-surface p-2 shadow-2xl group-data-[collapsible=icon]:min-w-56"
      role="menu"
    >
      <div class="px-2 py-2">
        <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
          Workspaces
        </p>
      </div>

      <div class="my-2 border-t border-border-subtle" />

      <div class="space-y-1">
        <button
          v-for="ws in options"
          :key="ws.workspaceId"
          role="menuitem"
          class="flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left text-sm transition-all"
          :class="activeWorkspace?.workspaceId === ws.workspaceId
            ? 'border-border-visible bg-bg-primary text-text-display'
            : 'border-transparent text-text-secondary hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display'"
          type="button"
          @click="selectWorkspace(ws)"
        >
          <WorkspaceAvatar
            :name="ws.name"
            :icon="ws.icon"
            size="sm"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-current">
              {{ ws.name }}
            </p>
            <p class="truncate text-[10px] text-text-secondary">
              {{ ws.role }}
            </p>
          </div>
        </button>

        <p
          v-if="isEmpty"
          class="px-3 py-4 text-center text-xs text-text-secondary"
        >
          No workspaces found
        </p>
      </div>

      <div class="my-2 border-t border-border-subtle" />

      <button
        class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
        type="button"
      >
        <Plus class="size-4 shrink-0" />
        <span>Add workspace</span>
      </button>
    </div>

    <button
      ref="triggerRef"
      class="flex w-full items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/70 px-3 py-2 transition-all hover:border-border-visible hover:bg-bg-surface group-data-[collapsible=icon]:p-0 group-data-[collapsible=icon]:size-10 group-data-[collapsible=icon]:justify-center"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      aria-controls="sidebar-workspace-menu"
      @click.stop="toggle"
    >
      <WorkspaceAvatar
        :name="activeWorkspace?.name ?? 'W'"
        :icon="activeWorkspace?.icon"
        size="md"
      />
      <span class="sr-only">{{ activeWorkspace?.name ?? 'Select workspace' }}</span>

      <div class="min-w-0 flex-1 text-left group-data-[collapsible=icon]:hidden">
        <p class="truncate font-mono text-[11px] font-bold uppercase tracking-[0.18em] text-text-display">
          {{ activeWorkspace?.name ?? 'Select workspace' }}
        </p>
        <p class="truncate text-xs text-text-secondary">
          {{ activeWorkspace?.role ?? '' }}
        </p>
      </div>

      <ChevronsUpDown class="size-4 shrink-0 text-text-secondary group-data-[collapsible=icon]:hidden" />
    </button>
  </div>
</template>
