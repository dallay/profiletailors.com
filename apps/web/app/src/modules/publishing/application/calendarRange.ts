import {
  CalendarDate,
  getDayOfWeek,
  startOfMonth,
  type CalendarDate as CalendarDateType,
} from '@internationalized/date'

export type CalendarSurface = 'week' | 'month'

export type CalendarRange = {
  from: string
  to: string
}

function parseCalendarDate(value: string): CalendarDateType {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) throw new Error('Invalid calendar date')

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new CalendarDate(year, month, day)
  if (date.year !== year || date.month !== month || date.day !== day) {
    throw new Error('Invalid calendar date')
  }
  return date
}

function atStartOfDay(date: CalendarDateType, timezone: string): string {
  return date.toDate(timezone).toISOString()
}

export function getCalendarRange(
  dateValue: string,
  surface: CalendarSurface,
  timezone: string,
): CalendarRange {
  const date = parseCalendarDate(dateValue)
  const start =
    surface === 'month'
      ? startOfMonth(date)
      : date.subtract({ days: getDayOfWeek(date, 'en-US', 'sun') })
  const end = surface === 'month' ? start.add({ months: 1 }) : start.add({ days: 7 })

  return {
    from: atStartOfDay(start, timezone),
    to: atStartOfDay(end, timezone),
  }
}
