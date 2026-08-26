export default {
  title: 'Accept your invitation',
  description: 'Click below to accept the invitation and join your private-beta workspace.',
  submit: 'Accept invitation',
  submitting: 'Accepting…',
  accepted: 'Invitation accepted.',
  workspaceReady: 'Loading your workspace…',
  redirecting: 'Redirecting to your workspace…',
  checkingAvailability: 'Checking invitation acceptance availability…',
  unavailableTitle: 'Invitation acceptance is currently unavailable',
  unavailableMessage: 'We cannot accept invitations right now. Please try again later.',
  errors: {
    notAcceptable: 'This invitation link is no longer valid or has already been used.',
    notFound: 'We could not find this invitation. It may have been revoked or already accepted.',
    requiresLogin: 'Sign in to your account to accept this invitation.',
    rateLimited: 'Too many attempts. Please wait a moment before trying again.',
    missingToken:
      'The invitation link is missing its token. Use the original link from your email.',
    generic: 'We could not accept the invitation right now. Please try again.',
  },
} as const
