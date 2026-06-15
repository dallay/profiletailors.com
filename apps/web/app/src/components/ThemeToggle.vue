<script setup lang="ts">
import { useSettingsStore } from '@/stores/settings'
import { ref } from 'vue'

const settings = useSettingsStore()
const isAnimating = ref(false)

function getTheme(): 'dark' | 'light' {
  return settings.currentTheme === 'light' ? 'light' : 'dark'
}

function animateThemeChange(next: 'dark' | 'light') {
  // Directional wipe: dark→light from top, light→dark from bottom
  const fromTop = next === 'light'
  const clipStart = fromTop ? 'inset(0 0 100% 0)' : 'inset(100% 0 0 0)'
  const clipEnd = 'inset(0 0 0% 0)'

  // Graceful degradation — View Transitions not supported in Firefox < 126, Safari < 18
  if (!document.startViewTransition) {
    settings.setTheme(next)
    return
  }

  // Respect reduced motion — skip the clip-path wipe
  if (globalThis.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    settings.setTheme(next)
    return
  }

  isAnimating.value = true
  const transition = document.startViewTransition(() => {
    settings.setTheme(next)
  })

  transition.ready.then(() => {
    document.documentElement.animate(
      { clipPath: [clipStart, clipEnd] },
      {
        duration: 600,
        easing: 'ease-in-out',
        pseudoElement: '::view-transition-new(root)',
      },
    )
  })

  transition.finished.then(() => {
    isAnimating.value = false
  })
}

function handleClick() {
  const current = getTheme()
  animateThemeChange(current === 'dark' ? 'light' : 'dark')
}
</script>

<template>
  <button
    @click="handleClick"
    :disabled="isAnimating"
    class="p-1.5 hover:text-text-display transition-colors rounded-full border border-border-subtle focus:outline-none focus:ring-1 focus:ring-text-secondary cursor-pointer disabled:opacity-50"
    :aria-label="$t('dashboard.toggleTheme')"
    :title="$t('dashboard.toggleTheme')"
  >
    <!-- Sun icon when dark, Moon icon when light -->
    <svg
      v-if="settings.currentTheme === 'dark'"
      role="img"
      aria-label="Sun"
      class="size-4"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      viewBox="0 0 24 24"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        d="M12 3v2.25m0 13.5V21M5.136 5.136l1.591 1.591m9.09 9.09l1.591 1.591M3 12h2.25m13.5 0H21M5.136 18.864l1.591-1.591m9.09-9.09l1.591-1.591M12 7.5a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9Z"
      />
    </svg>
    <svg
      v-else
      role="img"
      aria-label="Moon"
      class="size-4"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      viewBox="0 0 24 24"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        d="M21.752 15.002A9.72 9.72 0 0 1 18 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 0 0 3 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 0 0 9.002-5.998Z"
      />
    </svg>
  </button>
</template>
