<script setup lang="ts">
import { ref } from 'vue'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const postText = ref('')
const selectedPlatforms = ref<string[]>(['twitter'])
const isScheduling = ref(false)
const showSuccess = ref(false)

const platformsList = [
  { id: 'twitter', label: 'X/Twitter' },
  { id: 'instagram', label: 'Instagram' },
  { id: 'linkedin', label: 'LinkedIn' },
  { id: 'facebook', label: 'Facebook' },
]

const recentPosts = ref([
  {
    id: 1,
    content: 'We are officially launching early access for Profile Tailors next week! Stay tuned.',
    platforms: ['twitter', 'linkedin'],
    time: 'Today, 14:00',
  },
  {
    id: 2,
    content: 'Swiss design principles applied to social media scheduling. No bloat, just speed.',
    platforms: ['instagram', 'twitter'],
    time: 'Tomorrow, 09:30',
  },
])

function togglePlatform(id: string) {
  if (selectedPlatforms.value.includes(id)) {
    selectedPlatforms.value = selectedPlatforms.value.filter((p) => p !== id)
  } else {
    selectedPlatforms.value.push(id)
  }
}

function handleSchedule() {
  if (!postText.value.trim()) return

  isScheduling.value = true
  setTimeout(() => {
    recentPosts.value.unshift({
      id: Date.now(),
      content: postText.value,
      platforms: [...selectedPlatforms.value],
      time: 'Scheduled',
    })
    postText.value = ''
    isScheduling.value = false
    showSuccess.value = true
    setTimeout(() => {
      showSuccess.value = false
    }, 3000)
  }, 1000)
}
</script>

<template>
  <div class="space-y-12">
    <!-- Welcome Header -->
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('dashboard.welcome') }}, acosta
      </h2>
      <p class="text-sm text-text-secondary">
        {{ $t('dashboard.subtitle') }}
      </p>
    </div>

    <!-- Grid Stats -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <Card>
        <CardHeader class="p-0">
          <CardTitle class="label-mono text-text-secondary text-[10px]">
            {{ $t('dashboard.scheduled') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 flex items-baseline gap-2">
          <span class="text-5xl font-doto text-text-display font-light">02</span>
          <span class="text-xs text-text-secondary font-mono">POSTS</span>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="p-0">
          <CardTitle class="label-mono text-text-secondary text-[10px]">
            {{ $t('dashboard.platforms') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 flex items-baseline gap-2">
          <span class="text-5xl font-doto text-text-display font-light">04</span>
          <span class="text-xs text-text-secondary font-mono">ACTIVE</span>
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
          <span class="text-xs text-text-secondary font-mono">AVG</span>
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

        <Card class="bg-bg-surface border border-border-subtle">
          <CardContent class="p-0 space-y-6">
            <!-- Composer textarea -->
            <textarea
              v-model="postText"
              :placeholder="$t('composer.placeholder')"
              rows="4"
              class="w-full bg-bg-primary border border-border-visible rounded-lg p-4 text-sm text-text-body placeholder:text-text-secondary focus:outline-none focus:border-text-display resize-none font-sans"
            ></textarea>

            <!-- Channels selection -->
            <div class="space-y-2">
              <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
                Select Channels
              </span>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="platform in platformsList"
                  :key="platform.id"
                  @click="togglePlatform(platform.id)"
                  class="border rounded-full px-3 py-1 font-mono text-[9px] tracking-wider uppercase transition-colors cursor-pointer"
                  :class="selectedPlatforms.includes(platform.id)
                    ? 'border-text-display bg-text-display text-bg-primary font-bold'
                    : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary'"
                >
                  {{ platform.label }}
                </button>
              </div>
            </div>

            <!-- Submit action -->
            <div class="space-y-3">
              <Button
                @click="handleSchedule"
                :disabled="isScheduling || !postText.trim()"
                class="w-full justify-center"
              >
                {{ isScheduling ? '...' : $t('composer.scheduleBtn') }}
              </Button>

              <div
                v-if="showSuccess"
                class="border border-success/30 bg-success/10 text-success text-[11px] font-mono text-center py-2 rounded-md uppercase tracking-wider"
              >
                {{ $t('composer.successMsg') }}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
