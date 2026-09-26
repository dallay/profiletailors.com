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

/**
 * Parses an exact YYYY-MM-DD date, rejecting malformed or normalized dates with an Error.
 */
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

/**
 * Returns the date's start in the requested timezone as a UTC ISO timestamp.
 * Timezone conversion errors propagate.
 */
function atStartOfDay(date: CalendarDateType, timezone: string): string {
  return date.toDate(timezone).toISOString()
}

/**
 * Returns UTC ISO boundaries for the month or Sunday-based week containing dateValue.
 * The start is inclusive and the end is exclusive, using calendar days in timezone.
 *
 * @throws If dateValue is not a valid YYYY-MM-DD date or timezone conversion fails.
 */
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
