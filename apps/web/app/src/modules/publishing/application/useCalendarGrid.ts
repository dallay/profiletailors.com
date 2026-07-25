import { computed, ref } from 'vue'

export interface CalendarGrid {
  weeks: Date[][]
}

export function useCalendarGrid(baseDate: globalThis.Ref<Date>) {
  const monthGrid = computed(() => {
    const year = baseDate.value.getFullYear()
    const month = baseDate.value.getMonth()

    const firstDay = new Date(year, month, 1)
    const lastDay = new Date(year, month + 1, 0)

    // Start from the Sunday before (or on) the 1st
    const start = new Date(firstDay)
    start.setDate(start.getDate() - start.getDay())

    const weeks: Date[][] = []
    let current: Date[] = []
    const cursor = new Date(start)

    while (cursor <= lastDay || (current.length > 0 && cursor.getDay() !== 0)) {
      if (cursor.getDay() === 0 && current.length > 0) {
        weeks.push(current)
        current = []
      }
      current.push(new Date(cursor))
      cursor.setDate(cursor.getDate() + 1)
      // Safety: cap at 42 cells (6 weeks)
      if (weeks.length === 6 && current.length === 7) {
        current = []
      }
    }
    if (current.length > 0) weeks.push(current)

    // Pad to avoid flickering grids
    while (weeks.length < 6) {
      const lastWeek = weeks[weeks.length - 1]
      const lastDay = lastWeek?.[lastWeek.length - 1]
      const startOfPad = lastDay ? new Date(lastDay) : new Date(start)
      startOfPad.setDate(startOfPad.getDate() + 1)
      const padWeek: Date[] = []
      for (let i = 0; i < 7; i++) {
        const d = new Date(startOfPad)
        d.setDate(startOfPad.getDate() + i)
        padWeek.push(d)
      }
      weeks.push(padWeek)
    }

    return weeks
  })

  const weekDays = computed(() => {
    const days: Date[] = []
    const startOfWeek = new Date(baseDate.value)
    const dayOffset = startOfWeek.getDay()
    startOfWeek.setDate(startOfWeek.getDate() - dayOffset)

    for (let i = 0; i < 7; i++) {
      const d = new Date(startOfWeek)
      d.setDate(startOfWeek.getDate() + i)
      days.push(d)
    }
    return days
  })

  const isCurrentMonth = (d: Date): boolean => {
    return (
      d.getMonth() === baseDate.value.getMonth() &&
      d.getFullYear() === baseDate.value.getFullYear()
    )
  }

  const isCurrentWeek = (d: Date): boolean => {
    const start = weekDays.value[0]
    const end = weekDays.value[weekDays.value.length - 1]
    if (!start || !end) return false
    return d >= start && d <= end
  }

  const isToday = (d: Date): boolean => {
    const today = new Date()
    return (
      d.getDate() === today.getDate() &&
      d.getMonth() === today.getMonth() &&
      d.getFullYear() === today.getFullYear()
    )
  }

  return {
    monthGrid,
    weekDays,
    isCurrentMonth,
    isCurrentWeek,
    isToday,
  }
}
