/**
 * Domain type for a connected social channel.
 *
 * Represents a social media account the user has connected to Profile Tailors.
 * Used across application, infrastructure, and presentation layers.
 */

export type ChannelProvider = string

export type Channel = {
  id: string
  name: string
  provider: ChannelProvider
  avatar: string
  avatarUrl?: string
  handle: string
  status:
    | 'ACTIVE'
    | 'INACTIVE'
    | 'PENDING'
    | 'DISABLED'
    | 'REQUIRES_RECONNECT'
    | 'DELETED'
    | 'ERROR'
    | 'REVOKED'
    | 'EXPIRED'
  accountId: string // Maps to backend socialAccountId if available
  maxAttachments?: number
}
