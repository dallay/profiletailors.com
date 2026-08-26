export interface PublicCapabilities {
  registrationEnabled: boolean
  passwordRecoveryEnabled: boolean
  invitationAcceptanceEnabled: boolean
}

export interface PublicCapabilitiesDto {
  registrationEnabled?: unknown
  passwordRecoveryEnabled?: unknown
  invitationAcceptanceEnabled?: unknown
}
