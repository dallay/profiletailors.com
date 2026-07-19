export const LEGAL_PUBLICATION_STATUS = {
  APPROVED: 'approved',
  BLOCKED: 'blocked',
} as const

export type LegalPublicationStatus =
  (typeof LEGAL_PUBLICATION_STATUS)[keyof typeof LEGAL_PUBLICATION_STATUS]

export const legalPublicationStatus: LegalPublicationStatus =
  LEGAL_PUBLICATION_STATUS.APPROVED

/** Returns whether legally approved policy bodies may be rendered publicly. */
export function isLegalPublicationApproved(): boolean {
  return legalPublicationStatus === LEGAL_PUBLICATION_STATUS.APPROVED
}
