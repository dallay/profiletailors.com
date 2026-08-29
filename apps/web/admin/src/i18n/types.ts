export type MessageSchema = {
  nav: {
    dashboard: string
    waitlist: string
    users: string
    audit: string
    platformAdministration: string
  }
  common: {
    loading: string
    error: string
    noData: string
    confirm: string
    cancel: string
    save: string
    close: string
    actions: string
    status: string
    email: string
    createdAt: string
    updatedAt: string
    page: string
    of: string
    next: string
    previous: string
  }
  auth: {
    signIn: string
    signOut: string
    platformAdmin: string
    email: string
    password: string
    emailRequired: string
    emailInvalid: string
    passwordRequired: string
    invalidCredentials: string
    loginError: string
    signingIn: string
    show: string
    hide: string
    showPassword: string
    hidePassword: string
    accessDenied: string
    accessDeniedMessage: string
    notAuthenticated: string
  }
  dashboard: {
    title: string
    pendingEntries: string
    invitedEntries: string
    convertedEntries: string
    cancelledEntries: string
    activeInvitations: string
    expiringIn24h: string
    expiringIn7d: string
    failedDeliveries: string
    registrationsInPeriod: string
    period: string
    days: string
  }
  waitlist: {
    title: string
    entryId: string
    joinedAt: string
    invitedAt: string
    convertedAt: string
    cancelledAt: string
    source: string
    locale: string
    invite: string
    resend: string
    revoke: string
    cancel: string
    inviteConfirmTitle: string
    inviteConfirmMessage: string
    cancelConfirmTitle: string
    cancelConfirmMessage: string
    cancelReason: string
    cancelReasonRequired: string
    revokeConfirmTitle: string
    revokeConfirmMessage: string
    entries: string
    invitationHistory: string
    statuses: { pending: string; invited: string; converted: string; cancelled: string }
    filters: { status: string; search: string; all: string }
  }
  users: {
    title: string
    principalId: string
    displayName: string
    principalType: string
    lastAuthenticated: string
    authMethods: string
    workspaces: string
    platformRoles: string
    workspaceMemberships: string
  }
  audit: {
    title: string
    eventId: string
    occurredAt: string
    operator: string
    action: string
    targetType: string
    targetId: string
    result: string
    reason: string
    filterByAction: string
    allResults: string
    results: { succeeded: string; rejected: string; failed: string }
  }
  operators: {
    title: string
    assignRole: string
    revokeRole: string
    confirmAssign: string
    confirmRevoke: string
  }
  errors: {
    PLATFORM_ACCESS_DENIED: string
    WAITLIST_ENTRY_NOT_FOUND: string
    WAITLIST_ENTRY_ALREADY_CONVERTED: string
    WAITLIST_ENTRY_ALREADY_CANCELLED: string
    INVITATION_NOT_FOUND: string
    INVITATION_ALREADY_ACTIVE: string
    INVITATION_RATE_LIMIT_EXCEEDED: string
    INTERNAL_ERROR: string
  }
}
