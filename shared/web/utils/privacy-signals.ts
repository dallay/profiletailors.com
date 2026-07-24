/**
 * Check if Do Not Track (DNT) is enabled.
 * Returns false in SSR context.
 */
export function isDNTEnabled(): boolean {
  if (typeof navigator === 'undefined') {
    return false
  }

  return (
    navigator.doNotTrack === '1' ||
    navigator.doNotTrack === 'yes' ||
    (window as any).doNotTrack === '1'
  )
}

/**
 * Check if Global Privacy Control (GPC) is enabled.
 * Returns false in SSR context.
 */
export function isGPCEnabled(): boolean {
  if (typeof navigator === 'undefined') {
    return false
  }

  return (navigator as any).globalPrivacyControl === true
}

/**
 * Check if any privacy signal (DNT or GPC) is active.
 * Returns true if either signal is present.
 */
export function hasPrivacySignal(): boolean {
  return isDNTEnabled() || isGPCEnabled()
}
