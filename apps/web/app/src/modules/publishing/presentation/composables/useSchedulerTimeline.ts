import {
  ref,
  computed,
  onMounted,
  onUnmounted,
  watch,
  nextTick,
  getCurrentInstance,
  type Ref,
} from 'vue'

export type HourSlot = { hour: number; label: string }

export type SchedulerTimelineOptions = {
  days: Ref<Date[]>
  hourSlots: Ref<HourSlot[]>
  isToday: (day: Date) => boolean
}

export type SchedulerTimelineReturn = {
  now: Ref<Date>
  viewportRef: Ref<HTMLElement | null>
  todayHeaderRef: Ref<HTMLElement | null>
  hasToday: Ref<boolean>
  nowTopPx: Ref<number>
  nowFormattedTime: Ref<string>
  scrollToCurrentTimeAndToday: () => void
}

export function useSchedulerTimeline(options: SchedulerTimelineOptions): SchedulerTimelineReturn {
  const viewportRef = ref<HTMLElement | null>(null)
  const todayHeaderRef = ref<HTMLElement | null>(null)

  const now = ref(new Date())
  let timer: ReturnType<typeof setInterval> | null = null

  function scrollToCurrentTimeAndToday() {
    if (!viewportRef.value) return

    const currentHour = now.value.getHours()
    const targetTop = Math.max(0, (currentHour - 1) * 96)
    viewportRef.value.scrollTop = targetTop

    if (todayHeaderRef.value) {
      todayHeaderRef.value.scrollIntoView?.({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'center',
      })
    }
  }

  const startTimer = () => {
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      now.value = new Date()
    }, 30_000)
    scrollToCurrentTimeAndToday()
  }

  const stopTimer = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  if (getCurrentInstance()) {
    onMounted(startTimer)
    onUnmounted(stopTimer)
  } else {
    startTimer()
  }

  watch(
    [options.days, options.hourSlots],
    () => {
      nextTick(() => {
        scrollToCurrentTimeAndToday()
      })
    },
    { deep: true },
  )

  const hasToday = computed(() => options.days.value.some((d) => options.isToday(d)))

  const nowTopPx = computed(() => {
    const current = now.value
    const minutes = current.getHours() * 60 + current.getMinutes()
    return (minutes / 60) * 96
  })

  const nowFormattedTime = computed(() => {
    return now.value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  })

  return {
    now,
    viewportRef,
    todayHeaderRef,
    hasToday,
    nowTopPx,
    nowFormattedTime,
    scrollToCurrentTimeAndToday,
  }
}

export function createSchedulerTimeline(options: SchedulerTimelineOptions) {
  return useSchedulerTimeline(options)
}
