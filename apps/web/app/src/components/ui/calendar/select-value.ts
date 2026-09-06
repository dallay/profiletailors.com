export function selectNumberValue(e: Event | undefined): number {
  const target = e?.target
  if (target instanceof HTMLSelectElement) return Number(target.value)
  return Number.NaN
}
