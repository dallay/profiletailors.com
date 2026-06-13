<script setup lang="ts">
import { ref, computed } from 'vue'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth'
import { usePublishingStore } from '@/stores/publishing'
import CreatePostModal from '@/components/CreatePostModal.vue'
import QuickStart from '@/components/QuickStart.vue'

const auth = useAuthStore()
const publishingStore = usePublishingStore()

const postText = ref('')
const selectedPlatforms = ref<string[]>(['twitter', 'linkedin'])
const isModalOpen = ref(false)

const platformsList = [
  { id: 'twitter', label: 'X/Twitter' },
  { id: 'instagram', label: 'Instagram' },
  { id: 'linkedin', label: 'LinkedIn' },
  { id: 'facebook', label: 'Facebook' },
]

// Computed stats from store
const queuedPostsCount = computed(() => {
  const count = publishingStore.publications.filter((p) => p.status === 'QUEUED').length
  return count < 10 ? `0${count}` : String(count)
})

const activeChannelsCount = computed(() => {
  const count = publishingStore.channels.filter((c) => c.status === 'ACTIVE').length
  return count < 10 ? `0${count}` : String(count)
})

const recentPosts = computed(() => {
  return publishingStore.publications.slice(0, 5).map((pub) => {
    let relativeTime = 'Scheduled'
    try {
      const pubDate = new Date(pub.scheduledAt)
      relativeTime = pubDate.toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      // ignore
    }
    return {
      id: pub.id,
      content: pub.content,
      platforms: pub.channels,
      time: relativeTime,
      status: pub.status,
    }
  })
})

function togglePlatform(id: string) {
  if (selectedPlatforms.value.includes(id)) {
    selectedPlatforms.value = selectedPlatforms.value.filter((p) => p !== id)
  } else {
    selectedPlatforms.value.push(id)
  }
}

function handleOpenModal() {
  isModalOpen.value = true
}

function handleCreated() {
  postText.value = ''
  isModalOpen.value = false
}
</script>

<template>
  <div class="space-y-12">
    <!-- Welcome Header -->
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('dashboard.welcome') }}, {{ auth.displayName }}
      </h2>
      <p class="text-sm text-text-secondary">
        {{ $t('dashboard.subtitle') }}
      </p>
    </div>

    <!-- Quick Start Section -->
    <QuickStart />

    <!-- Grid Stats -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <Card>
        <CardHeader class="p-0">
          <CardTitle class="label-mono text-text-secondary text-[10px]">
            {{ $t('dashboard.scheduled') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 flex items-baseline gap-2">
          <span class="text-5xl font-doto text-text-display font-light">{{ queuedPostsCount }}</span>
          <span class="text-xs text-text-secondary font-mono">{{ $t('dashboard.posts') }}</span>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="p-0">
          <CardTitle class="label-mono text-text-secondary text-[10px]">
            {{ $t('dashboard.platforms') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 flex items-baseline gap-2">
          <span class="text-5xl font-doto text-text-display font-light">{{ activeChannelsCount }}</span>
          <span class="text-xs text-text-secondary font-mono">{{ $t('dashboard.active') }}</span>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="p-0">
          <CardTitle class="label-mono text-text-secondary text-[10px]">
            {{ $t('dashboard.audience') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 flex items-baseline gap-2">
          <span class="text-5xl font-doto text-text-display font-light">12.4K</span>
          <span class="text-xs text-success font-mono font-bold">+18%</span>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="p-0">
          <CardTitle class="label-mono text-text-secondary text-[10px]">
            {{ $t('dashboard.engagement') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 flex items-baseline gap-2">
          <span class="text-5xl font-doto text-text-display font-light">4.8%</span>
          <span class="text-xs text-text-secondary font-mono">{{ $t('dashboard.avg') }}</span>
        </CardContent>
      </Card>
    </div>

    <!-- Actions & Queue layout -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- Activity stream -->
      <div class="lg:col-span-2 space-y-6">
        <div class="flex items-center justify-between border-b border-border-subtle pb-4">
          <h3 class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
            {{ $t('dashboard.recentActivity') }}
          </h3>
          <button class="font-mono text-[10px] tracking-wider text-text-secondary hover:text-text-display uppercase cursor-pointer">
            [ {{ $t('dashboard.viewAll') }} ]
          </button>
        </div>

        <div class="space-y-4">
          <div
            v-for="post in recentPosts"
            :key="post.id"
            class="border border-border-subtle bg-bg-surface rounded-xl p-6 space-y-4"
          >
            <div class="flex items-center justify-between">
              <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase">
                {{ post.time }}
              </span>
              <div class="flex gap-2">
                <span
                  v-for="platform in post.platforms"
                  :key="platform"
                  class="border border-border-visible px-2 py-0.5 rounded-full font-mono text-[8px] tracking-wider text-text-secondary uppercase bg-bg-primary"
                >
                  {{ platform }}
                </span>
                <span
                  class="border border-border-visible px-2 py-0.5 rounded-full font-mono text-[8px] tracking-wider text-text-secondary uppercase"
                  :class="post.status === 'PUBLISHED' ? 'bg-success/10 text-success' : 'bg-transparent text-text-secondary'"
                >
                  {{ post.status }}
                </span>
              </div>
            </div>
            <p class="text-sm text-text-body font-light leading-relaxed">
              {{ post.content }}
            </p>
          </div>
        </div>
      </div>

      <!-- Composer Panel -->
      <div class="space-y-6">
        <div class="border-b border-border-subtle pb-4">
          <h3 class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
            {{ $t('composer.title') }}
          </h3>
        </div>

        <Card class="bg-bg-surface border border-border-subtle hover:border-text-secondary transition-all cursor-pointer" @click="handleOpenModal">
          <CardContent class="p-0 space-y-6">
            <!-- Composer textarea placeholder -->
            <div class="w-full bg-bg-primary border border-border-visible rounded-lg p-4 text-sm text-text-secondary font-sans min-h-[96px] select-none">
              {{ $t('composer.placeholder') }}
            </div>

            <!-- Channels selection -->
            <div class="space-y-2">
              <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
                {{ $t('dashboard.selectChannels') }}
              </span>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="platform in platformsList"
                  :key="platform.id"
                  @click.stop="togglePlatform(platform.id)"
                  class="border rounded-full px-3 py-1 font-mono text-[9px] tracking-wider uppercase transition-colors cursor-pointer"
                  :class="selectedPlatforms.includes(platform.id)
                    ? 'border-text-display bg-text-display text-bg-primary font-bold'
                    : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary'"
                >
                  {{ platform.label }}
                </button>
              </div>
            </div>

            <!-- Trigger Button -->
            <div>
              <Button class="w-full justify-center">
                {{ $t('composer.title') }}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Create Post Modal Dialog -->
    <CreatePostModal
      :is-open="isModalOpen"
      @close="isModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
