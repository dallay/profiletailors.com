/**
 * Commerce publication gate.
 *
 * Controls whether commercial terms (pricing, subscriptions, billing pages)
 * may be rendered publicly on the marketing site.
 *
 * Flip to APPROVED only when pricing and subscription terms are defined
 * and approved in the Terms of Service and related legal documents.
 *
 * @see ToS Section 5 (Pricing and Payments) / ToS Sección 5 (Precios y Pagos)
 */

export const COMMERCE_PUBLICATION_STATUS = {
  APPROVED: 'approved',
  BLOCKED: 'blocked',
} as const

export type CommercePublicationStatus =
  (typeof COMMERCE_PUBLICATION_STATUS)[keyof typeof COMMERCE_PUBLICATION_STATUS]

/**
 * Current publication status for commerce-related content.
 *
 * BLOCKED by default — commercial terms are not yet defined in the ToS.
 * Switch to APPROVED when pricing and subscription language is finalized.
 */
export const commercePublicationStatus: CommercePublicationStatus =
  COMMERCE_PUBLICATION_STATUS.BLOCKED

/** Returns whether commerce-related content may be rendered publicly. */
export function isCommercePublicationApproved(): boolean {
  return commercePublicationStatus === COMMERCE_PUBLICATION_STATUS.APPROVED
}
