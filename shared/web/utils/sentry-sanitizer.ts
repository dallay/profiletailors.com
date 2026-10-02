const MAX_STRING_LENGTH = 512

const SAFE_HEADER_NAMES = new Set(['accept', 'content-type'])
const SAFE_TAG_NAMES = new Set([
  'environment',
  'release',
  'principalid',
  'workspaceid',
  'publicationid',
  'jobid',
  'invitationid',
])
const URL_FIELD_NAMES = new Set(['url', 'href', 'requesturl', 'referrer', 'referer'])
const HTTP_DATA_PARENT_NAMES = new Set(['request', 'response'])
const SENSITIVE_FIELD_NAMES = [
  'authorization',
  'password',
  'passwd',
  'token',
  'secret',
  'apikey',
  'credential',
  'cookie',
  'email',
  'jwt',
  'encryptionkey',
  'ipaddress',
  'clientip',
  'clientaddress',
  'remoteaddress',
  'forwardedfor',
  'reseturl',
  'oauthcode',
  'authorizationcode',
]
const SENSITIVE_PAYLOAD_NAMES = [
  'body',
  'payload',
  'media',
  'attachment',
  'query',
  'fragment',
  'extra',
]
const REQUEST_DATA_NAMES = new Set(['data', 'body', 'query', 'querystring', 'cookies'])
const EMAIL_PATTERN = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi
const URL_PATTERN = /https?:\/\/[^\s<>"'`]+/gi
const BEARER_PATTERN = /\bBearer\s+[^\s,;]+/gi
const JWT_PATTERN = /\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g
const CREDENTIAL_VALUE_PATTERN =
  /\b((?:password|passwd|passphrase|access[\s_-]?token|refresh[\s_-]?token|id[\s_-]?token|token|api[\s_-]?key|client[\s_-]?secret|encryption[\s_-]?key|secret|authorization|cookie|set[\s_-]?cookie|pt[\s_-]?refresh|code|oauth[\s_-]?code)\s*[:=]\s*)["']?[^"'\s,;&]+["']?/gi
const IPV4_PATTERN =
  /\b(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\b/g
const IPV6_CANDIDATE_PATTERN = /(?<![0-9a-f:])[0-9a-f:]{2,}(?![0-9a-f:])/gi
const URL_PUNCTUATION_PATTERN = /[),.!?;:]+$/

export function sanitizeSentryPayload<T extends object>(payload: T): boolean {
  return sanitizeObject(payload, new WeakSet(), '')
}

function sanitizeObject(value: object, visited: WeakSet<object>, parentKey: string): boolean {
  if (visited.has(value)) return true
  visited.add(value)

  for (const propertyKey of Reflect.ownKeys(value)) {
    if (typeof propertyKey !== 'string') continue

    const key = normalizeKey(propertyKey)
    if (isSensitiveField(key) || isSensitivePayload(key)) {
      if (!Reflect.deleteProperty(value, propertyKey)) return false
      continue
    }

    if (HTTP_DATA_PARENT_NAMES.has(parentKey) && REQUEST_DATA_NAMES.has(key)) {
      if (!Reflect.deleteProperty(value, propertyKey)) return false
      continue
    }

    const child: unknown = Reflect.get(value, propertyKey)
    if (key === 'user') {
      if (!sanitizeUser(child, visited)) return false
    } else if (key === 'headers') {
      if (!sanitizeHeaders(child, visited)) return false
    } else if (key === 'tags') {
      if (!sanitizeTags(child, visited)) return false
    } else if (typeof child === 'string') {
      if (!Reflect.set(value, propertyKey, sanitizeText(child, key))) return false
    } else if (isObject(child) && !sanitizeObject(child, visited, key)) {
      return false
    }
  }

  return true
}

function sanitizeUser(value: unknown, visited: WeakSet<object>): boolean {
  if (!isObject(value)) return true

  for (const propertyKey of Reflect.ownKeys(value)) {
    if (propertyKey !== 'id' && !Reflect.deleteProperty(value, propertyKey)) return false
  }

  const id: unknown = Reflect.get(value, 'id')
  if (typeof id !== 'string' || !/^[A-Za-z0-9:_-]{1,128}$/.test(id)) {
    return Reflect.deleteProperty(value, 'id')
  }

  if (!Reflect.set(value, 'id', sanitizeText(id, 'id'))) return false
  visited.add(value)
  return true
}

function sanitizeHeaders(value: unknown, visited: WeakSet<object>): boolean {
  if (!isObject(value)) return true

  for (const propertyKey of Reflect.ownKeys(value)) {
    if (typeof propertyKey !== 'string') continue
    const key = propertyKey.toLowerCase()
    const headerValue: unknown = Reflect.get(value, propertyKey)

    if (!SAFE_HEADER_NAMES.has(key) || typeof headerValue !== 'string') {
      if (!Reflect.deleteProperty(value, propertyKey)) return false
      continue
    }

    if (!Reflect.set(value, propertyKey, sanitizeText(headerValue, key))) return false
  }

  visited.add(value)
  return true
}

function sanitizeTags(value: unknown, visited: WeakSet<object>): boolean {
  if (!isObject(value)) return true

  for (const propertyKey of Reflect.ownKeys(value)) {
    if (typeof propertyKey !== 'string') continue
    const tagValue: unknown = Reflect.get(value, propertyKey)
    const key = normalizeKey(propertyKey)

    if (!SAFE_TAG_NAMES.has(key) || typeof tagValue !== 'string') {
      if (!Reflect.deleteProperty(value, propertyKey)) return false
      continue
    }

    const maximumLength = key === 'environment' || key === 'release' ? MAX_STRING_LENGTH : 128
    if (!Reflect.set(value, propertyKey, sanitizeText(tagValue, key).slice(0, maximumLength)))
      return false
  }

  visited.add(value)
  return true
}

function sanitizeText(value: string, key = ''): string {
  const withoutAbsoluteUrlParameters = value.replace(URL_PATTERN, sanitizeUrl)
  const withoutRelativeUrlParameters = URL_FIELD_NAMES.has(key)
    ? withoutAbsoluteUrlParameters.replace(/[?#].*$/, '')
    : withoutAbsoluteUrlParameters

  return withoutRelativeUrlParameters
    .replace(BEARER_PATTERN, '[redacted]')
    .replace(JWT_PATTERN, '[redacted]')
    .replace(IPV4_PATTERN, '[redacted]')
    .replace(IPV6_CANDIDATE_PATTERN, (candidate) =>
      isIpv6Literal(candidate) ? '[redacted]' : candidate,
    )
    .replace(CREDENTIAL_VALUE_PATTERN, (_match, prefix: string) => `${prefix}[redacted]`)
    .replace(EMAIL_PATTERN, '[redacted]')
    .slice(0, MAX_STRING_LENGTH)
}

function sanitizeUrl(value: string): string {
  const trailingPunctuation = URL_PUNCTUATION_PATTERN.exec(value)?.[0] ?? ''
  const address = trailingPunctuation ? value.slice(0, -trailingPunctuation.length) : value

  try {
    const url = new URL(address)
    return `${url.origin}${url.pathname}${trailingPunctuation}`
  } catch {
    return `${address.replace(/[?#].*$/, '')}${trailingPunctuation}`
  }
}

function isSensitiveField(key: string): boolean {
  return (
    SENSITIVE_FIELD_NAMES.some((field) => key.includes(field)) ||
    key === 'ip' ||
    key === 'rawip' ||
    key === 'remoteip' ||
    key === 'xforwardedfor' ||
    key === 'code'
  )
}

function isSensitivePayload(key: string): boolean {
  return SENSITIVE_PAYLOAD_NAMES.some((field) => key.includes(field))
}

function normalizeKey(key: string): string {
  return key.toLowerCase().replace(/[^a-z0-9]/g, '')
}

function isObject(value: unknown): value is object {
  return typeof value === 'object' && value !== null
}

function isIpv6Literal(value: string): boolean {
  try {
    return new URL(`http://[${value}]/`).hostname !== ''
  } catch {
    return false
  }
}
