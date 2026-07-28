<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@layouts/AppShell.vue'

const route = useRoute()

const isStandaloneRoute = computed(() => route.meta.standalone === true)
</script>

<template>
  <RouterView v-if="isStandaloneRoute" />
  <AppShell v-else />
</template>
