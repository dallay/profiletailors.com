import { computed, nextTick, onMounted, ref, watch } from 'vue'

type SchedulerTimelineProps = {
  days: Date[]
  isToday: (day: Date) => boolean
  now: Date
}

export function useSchedulerTimeline(props: Readonly<SchedulerTimelineProps>) {
  const viewport = ref<HTMLElement | null>(null)
  const nowMarkerStyle = computed(() => ({ top: `${(props.now.getMinutes() / 60) * 100}%` }))
  function revealToday(): void {
    const todayIndex = props.days.findIndex((day) => props.isToday(day))
    if (todayIndex < 0 || !viewport.value) return
    const dayWidth = 120
    const gutterWidth = 48
    const todayStart = gutterWidth + todayIndex * dayWidth
    const todayEnd = todayStart + dayWidth
    const visibleStart = viewport.value.scrollLeft
    const visibleEnd = visibleStart + viewport.value.clientWidth
    if (todayStart < visibleStart + gutterWidth) {
      viewport.value.scrollLeft = Math.max(0, todayStart - gutterWidth)
    } else if (todayEnd > visibleEnd)
      viewport.value.scrollLeft = todayEnd - viewport.value.clientWidth
  }
  watch(
    () => props.days,
    () => nextTick(revealToday),
    { immediate: true },
  )
  onMounted(revealToday)

  return { viewport, nowMarkerStyle }
}
