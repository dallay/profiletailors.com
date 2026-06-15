<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/settings'
import { usePublishingStore } from '@/stores/publishing'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'
import { renameWorkspace, updateWorkspaceIcon, proxyImageUrl } from '@/lib/auth-api'
import WorkspaceAvatar from '@/components/WorkspaceAvatar.vue'
import { toPascalCase } from '@/lib/string-utils'
import * as LucideIcons from '@lucide/vue'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const settings = useSettingsStore()
const publishing = usePublishingStore()
const auth = useAuthStore()
const workspace = useWorkspaceStore()
const route = useRoute()
const { t } = useI18n()
const connectError = ref<string | null>(null)
const connectingLinkedIn = ref(false)

// Workspace rename
const editingWorkspaceName = ref(false)
const workspaceNameInput = ref('')
const renamingWorkspace = ref(false)
const renameError = ref<string | null>(null)
const renameSuccess = ref(false)
let renameSuccessTimer: number | undefined

onBeforeUnmount(() => {
  if (renameSuccessTimer !== undefined) {
    clearTimeout(renameSuccessTimer)
  }
})

const displayWorkspaceName = computed(
  () => workspace.workspaceName || t('workspace.defaultName'),
)

const CURATED_ICONS = [
  'briefcase', 'building', 'palette', 'rocket', 'zap', 'star',
  'heart', 'flag', 'globe', 'compass', 'target', 'trending-up',
  'layers', 'folder', 'shield', 'users', 'camera', 'music',
  'code', 'book-open', 'crown', 'gem', 'sparkles', 'smile'
]

const updatingIcon = ref(false)
const iconError = ref<string | null>(null)

const workspaceIdentifier = computed(() => workspace.activeWorkspaceId ?? '—')
const connectedLinkedInCount = computed(() => linkedInChannels.value.length)
const channelStatusLabel = computed(() =>
  connectedLinkedInCount.value > 0
    ? `${String(connectedLinkedInCount.value).padStart(2, '0')} ${t('channels.active')}`
    : t('channels.noChannels'),
)

function segmentedControlClass(isActive: boolean) {
  return isActive
    ? 'bg-text-display text-bg-primary'
    : 'text-text-secondary hover:text-text-display'
}

async function selectIcon(iconName: string | null) {
  if (!auth.accessToken || !workspace.activeWorkspaceId) return

  updatingIcon.value = true
  iconError.value = null

  try {
    const result = await updateWorkspaceIcon(iconName, auth.accessToken, workspace.activeWorkspaceId)
    workspace.updateWorkspaceIcon(result.workspaceId, result.icon)
  } catch (err) {
    iconError.value = err instanceof Error ? err.message : t('workspace.updateIconFailed')
  } finally {
    updatingIcon.value = false
  }
}

function startRenameWorkspace() {
  workspaceNameInput.value = workspace.workspaceName || ''
  editingWorkspaceName.value = true
  renameError.value = null
  renameSuccess.value = false
}

function cancelRenameWorkspace() {
  editingWorkspaceName.value = false
  renameError.value = null
}

async function saveWorkspaceName() {
  const newName = workspaceNameInput.value.trim()
  if (!newName || !auth.accessToken || !workspace.activeWorkspaceId) return

  renamingWorkspace.value = true
  renameError.value = null
  renameSuccess.value = false

  try {
    const result = await renameWorkspace(newName, auth.accessToken, workspace.activeWorkspaceId)
    workspace.setWorkspaceName(result.name)
    editingWorkspaceName.value = false
    renameSuccess.value = true
    renameSuccessTimer = window.setTimeout(() => { renameSuccess.value = false }, 3000)
  } catch (err) {
    renameError.value = err instanceof Error ? err.message : t('workspace.renameFailed')
  } finally {
    renamingWorkspace.value = false
  }
}

const linkedInChannels = computed(() =>
  publishing.channels.filter((channel) => channel.provider === 'linkedin' && channel.status === 'ACTIVE'),
)
const linkedinConnected = computed(() => route.query.connected === 'linkedin')
const channelsPanelFocused = computed(() =>
  route.query.panel === 'channels' ||
  route.query.provider === 'linkedin' ||
  linkedinConnected.value,
)

async function connectLinkedInProfile() {
  connectError.value = null
  connectingLinkedIn.value = true

  try {
    await publishing.connectLinkedInPersonalProfile()
  } catch (err: unknown) {
    const e = err as { detail?: string; message?: string }
    connectError.value = e?.detail || e?.message || t('channels.connectLinkedInFailed')
    connectingLinkedIn.value = false
  }
}

onMounted(() => {
  publishing.fetchChannels().catch((err) => {
    connectError.value = err instanceof Error ? err.message : t('channels.loadFailed')
  })
  publishing.fetchConfiguredProviders().catch((err) => {
    console.error('Failed to load configured providers:', err)
  })
})
</script>

<template>
  <div data-testid="settings-shell" class="space-y-8">
    <section
      data-testid="settings-overview"
      class="rounded-[32px] border border-border-subtle bg-bg-surface/90 p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] sm:p-8"
    >
      <div class="grid gap-6 xl:grid-cols-[minmax(0,1.12fr)_minmax(300px,0.88fr)] xl:items-start">
        <div class="space-y-6">
          <div class="space-y-3 border-b border-border-subtle pb-6">
            <p class="label-mono text-text-secondary">
              {{ $t('settings.overviewBadge') }}
            </p>
            <h2 class="max-w-3xl text-3xl font-light tracking-tight text-text-display sm:text-4xl">
              {{ $t('nav.settings') }}
            </h2>
            <p class="max-w-2xl text-sm leading-6 text-text-secondary">
              {{ $t('settings.subtitle') }}
            </p>
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <article class="rounded-2xl border border-border-subtle bg-bg-primary/65 p-5">
              <p class="label-mono text-text-secondary">{{ $t('settings.channelStatusTitle') }}</p>
              <p class="mt-3 text-lg font-medium text-text-display">{{ channelStatusLabel }}</p>
              <p class="mt-2 text-sm leading-6 text-text-secondary">
                {{ linkedInChannels.length ? $t('channels.connectLinkedInProfileDesc') : $t('channels.noChannels') }}
              </p>
            </article>

            <article class="rounded-2xl border border-border-subtle bg-bg-primary/65 p-5">
              <p class="label-mono text-text-secondary">{{ $t('settings.workspaceIdentityTitle') }}</p>
              <div class="mt-3 flex items-center gap-4">
                <WorkspaceAvatar
                  :name="workspace.activeWorkspace?.name ?? 'W'"
                  :icon="workspace.activeWorkspace?.icon"
                  size="md"
                />
                <div class="min-w-0">
                  <p class="truncate text-base font-medium text-text-display">{{ displayWorkspaceName }}</p>
                  <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                    {{ workspaceIdentifier }}
                  </p>
                </div>
              </div>
            </article>
          </div>
        </div>

        <aside
          data-testid="settings-preferences-panel"
          class="rounded-[28px] border border-border-subtle bg-bg-primary/75 p-5 sm:p-6"
        >
          <p class="label-mono text-text-secondary">{{ $t('settings.preferencesEyebrow') }}</p>
          <h3 class="mt-2 text-xl font-light text-text-display">{{ $t('settings.interfacePreferences') }}</h3>

          <div class="mt-6 space-y-5">
            <div class="space-y-3">
              <div>
                <p class="text-sm font-medium text-text-display">{{ $t('settings.languageLabel') }}</p>
                <p class="mt-1 text-xs leading-5 text-text-secondary">{{ $t('settings.languageDesc') }}</p>
              </div>
              <div class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]">
                <button
                  type="button"
                  class="min-h-9 cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentLocale === 'en')"
                  :aria-pressed="settings.currentLocale === 'en'"
                  @click="settings.setLocale('en')"
                >
                  EN
                </button>
                <button
                  type="button"
                  class="min-h-9 cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentLocale === 'es')"
                  :aria-pressed="settings.currentLocale === 'es'"
                  @click="settings.setLocale('es')"
                >
                  ES
                </button>
              </div>
            </div>

            <div class="border-t border-border-subtle pt-5">
              <div>
                <p class="text-sm font-medium text-text-display">{{ $t('settings.themeLabel') }}</p>
                <p class="mt-1 text-xs leading-5 text-text-secondary">{{ $t('settings.themeDesc') }}</p>
              </div>
              <div class="mt-3 flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]">
                <button
                  type="button"
                  class="min-h-9 cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentTheme === 'dark')"
                  :aria-pressed="settings.currentTheme === 'dark'"
                  @click="settings.setTheme('dark')"
                >
                  {{ $t('settings.dark') }}
                </button>
                <button
                  type="button"
                  class="min-h-9 cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentTheme === 'light')"
                  :aria-pressed="settings.currentTheme === 'light'"
                  @click="settings.setTheme('light')"
                >
                  {{ $t('settings.light') }}
                </button>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] xl:items-start">
      <Card
        class="border border-border-subtle bg-bg-surface p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] transition-colors"
        :class="channelsPanelFocused
          ? 'shadow-[0_0_0_1px_rgba(255,255,255,0.12)]'
          : ''"
      >
        <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-5">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <CardTitle class="label-mono text-[10px] text-text-display">
                {{ $t('channels.title') }}
              </CardTitle>
              <p class="mt-3 max-w-lg text-sm leading-6 text-text-secondary">
                {{ $t('channels.connectLinkedInProfileDesc') }}
              </p>
            </div>
            <Button
              v-if="publishing.isLinkedInConfigured"
              type="button"
              :disabled="connectingLinkedIn"
              @click="connectLinkedInProfile"
            >
              {{ connectingLinkedIn ? $t('channels.connectingLinkedIn') : $t('channels.connectLinkedInProfile') }}
            </Button>
          </div>
        </CardHeader>

        <CardContent class="mt-6 space-y-5 p-0">
          <p
            v-if="linkedinConnected"
            class="rounded-2xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success"
          >
            {{ $t('linkedinCallback.successMessage') }}
          </p>

          <p
            v-if="publishing.channelsLoading"
            class="font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary"
          >
            {{ $t('channels.loading') }}
          </p>

          <div v-if="linkedInChannels.length" class="space-y-3">
            <div
              v-for="channel in linkedInChannels"
              :key="channel.id"
              class="flex items-center gap-4 rounded-2xl border border-border-subtle bg-bg-primary px-4 py-4"
            >
              <img
                v-if="channel.avatarUrl"
                :src="proxyImageUrl(channel.avatarUrl!)"
                :alt="`${channel.name} avatar`"
                class="size-11 rounded-full border border-border-visible object-cover"
              >
              <div
                v-else
                class="flex size-11 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-surface font-mono text-[10px] font-bold uppercase text-text-display"
              >
                in
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-text-display">{{ channel.name }}</p>
                <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                  {{ channel.handle }}
                </p>
              </div>
              <span class="rounded-full border border-border-visible px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em] text-success">
                {{ $t('channels.active') }}
              </span>
            </div>
          </div>

          <div
            v-else-if="!publishing.isLinkedInConfigured"
            class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/50 p-5"
          >
            <p class="text-sm font-medium text-text-display">{{ $t('channels.notConfigured') }}</p>
            <p class="mt-1 text-xs leading-5 text-text-secondary">
              {{ $t('channels.notConfiguredDesc') }}
            </p>
          </div>

          <div
            v-else
            class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/50 p-5"
          >
            <p class="text-sm font-medium text-text-display">{{ $t('channels.noChannels') }}</p>
            <p class="mt-1 text-xs leading-5 text-text-secondary">
              {{ $t('channels.connectLinkedInProfileDesc') }}
            </p>
          </div>

          <p v-if="connectError || publishing.channelsError" class="text-sm text-error">
            {{ connectError || publishing.channelsError }}
          </p>
        </CardContent>
      </Card>

      <Card class="border border-border-subtle bg-bg-surface p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]">
        <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-5">
          <CardTitle class="label-mono text-[10px] text-text-display">
            {{ $t('workspace.title') }}
          </CardTitle>
          <div class="flex items-center gap-4">
            <WorkspaceAvatar
              :name="workspace.activeWorkspace?.name ?? 'W'"
              :icon="workspace.activeWorkspace?.icon"
              size="md"
            />
            <div class="min-w-0">
              <p class="truncate text-sm font-medium text-text-display">{{ displayWorkspaceName }}</p>
              <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                {{ workspaceIdentifier }}
              </p>
            </div>
            <Button
              v-if="workspace.activeWorkspace?.icon"
              variant="outline"
              size="sm"
              class="ml-auto"
              :disabled="updatingIcon"
              @click="selectIcon(null)"
            >
              {{ $t('workspace.removeIcon') }}
            </Button>
          </div>
        </CardHeader>

        <CardContent class="mt-6 space-y-6 p-0">
          <div class="space-y-4">
            <div class="grid grid-cols-6 gap-2 sm:grid-cols-8">
              <button
                v-for="iconName in CURATED_ICONS"
                :key="iconName"
                type="button"
                :aria-label="iconName"
                class="flex size-10 items-center justify-center rounded-xl border transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                :class="workspace.activeWorkspace?.icon === iconName
                  ? 'border-text-display bg-text-display text-bg-primary'
                  : 'border-border-visible text-text-secondary hover:border-text-secondary hover:text-text-display'"
                :disabled="updatingIcon"
                @click="selectIcon(iconName)"
              >
                <component :is="LucideIcons[toPascalCase(iconName) as keyof typeof LucideIcons]" :size="18" />
              </button>
            </div>
            <p v-if="iconError" class="text-sm text-error">{{ iconError }}</p>
          </div>

          <div class="border-t border-border-visible pt-6">
            <p
              v-if="renameSuccess"
              class="mb-4 rounded-2xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success"
            >
              {{ $t('workspace.renameSuccess') }}
            </p>

            <div v-if="!editingWorkspaceName" class="flex items-center justify-between gap-4">
              <div class="min-w-0">
                <p class="text-sm font-medium text-text-display">{{ displayWorkspaceName }}</p>
                <p class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                  {{ workspaceIdentifier }}
                </p>
              </div>
              <Button variant="outline" size="sm" @click="startRenameWorkspace">
                {{ $t('workspace.rename') }}
              </Button>
            </div>

            <div v-else class="space-y-3">
              <input
                v-model="workspaceNameInput"
                type="text"
                :placeholder="$t('workspace.namePlaceholder')"
                class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                maxlength="255"
                @keyup.enter="saveWorkspaceName"
                @keyup.escape="cancelRenameWorkspace"
              >
              <p v-if="renameError" class="text-sm text-error">{{ renameError }}</p>
              <div class="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  :disabled="renamingWorkspace || !workspaceNameInput.trim()"
                  @click="saveWorkspaceName"
                >
                  {{ renamingWorkspace ? '...' : $t('workspace.save') }}
                </Button>
                <Button variant="outline" size="sm" @click="cancelRenameWorkspace">
                  {{ $t('workspace.cancel') }}
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
