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
const CREDENTIAL_VALUE_PATTERNS = [
  /\b((?:password|passwd|passphrase|token)\s*[:=]\s*)["']?[^"'\s,;&]+["']?/gi,
  /\b((?:secret|authorization|cookie|code)\s*[:=]\s*)["']?[^"'\s,;&]+["']?/gi,
  /\b((?:access|refresh|id)[\s_-]?token\s*[:=]\s*)["']?[^"'\s,;&]+["']?/gi,
  /\b((?:api[\s_-]?key|client[\s_-]?secret|encryption[\s_-]?key)\s*[:=]\s*)["']?[^"'\s,;&]+["']?/gi,
  /\b((?:set[\s_-]?cookie|pt[\s_-]?refresh|oauth[\s_-]?code)\s*[:=]\s*)["']?[^"'\s,;&]+["']?/gi,
]
const IPV4_CANDIDATE_PATTERN = /\b\d{1,3}(?:\.\d{1,3}){3}\b/g
const IPV6_CANDIDATE_PATTERN = /(?<![0-9a-f:])[0-9a-f:]{2,}(?![0-9a-f:])/gi
const URL_TRAILING_PUNCTUATION = new Set([')', ',', '.', '!', '?', ';', ':'])
const LINE_TERMINATORS = new Set(['\n', '\r', '\u2028', '\u2029'])

export function sanitizeSentryPayload<T extends object>(payload: T): boolean {
  return sanitizeObject(payload, new WeakSet(), '')
}

export interface RedactableSpan {
  trace_id: string
  span_id: string
  parent_span_id?: string
  start_timestamp: number
  end_timestamp?: number
  status: 'ok' | 'error'
  is_segment: boolean
}

export interface RedactedSpan {
  trace_id: string
  span_id: string
  parent_span_id?: string
  name: 'redacted'
  start_timestamp: number
  end_timestamp?: number
  status: 'ok' | 'error'
  is_segment: boolean
  attributes: Record<string, never>
}

export function buildRedactedSpan(span: RedactableSpan): RedactedSpan {
  return {
    trace_id: span.trace_id,
    ...(span.parent_span_id === undefined ? {} : { parent_span_id: span.parent_span_id }),
    span_id: span.span_id,
    name: 'redacted',
    start_timestamp: span.start_timestamp,
    ...(span.end_timestamp === undefined ? {} : { end_timestamp: span.end_timestamp }),
    status: span.status,
    is_segment: span.is_segment,
    attributes: {},
  }
}

function sanitizeObject(value: object, visited: WeakSet<object>, parentKey: string): boolean {
  if (visited.has(value)) return true
  visited.add(value)

  for (const propertyKey of Reflect.ownKeys(value)) {
    if (typeof propertyKey !== 'string') continue
    if (!sanitizeProperty(value, propertyKey, visited, parentKey)) return false
  }

  return true
}

function sanitizeProperty(
  target: object,
  propertyKey: string,
  visited: WeakSet<object>,
  parentKey: string,
): boolean {
  const key = normalizeKey(propertyKey)
  if (isSensitiveField(key) || isSensitivePayload(key)) {
    return Reflect.deleteProperty(target, propertyKey)
  }
  if (HTTP_DATA_PARENT_NAMES.has(parentKey) && REQUEST_DATA_NAMES.has(key)) {
    return Reflect.deleteProperty(target, propertyKey)
  }

  const child: unknown = Reflect.get(target, propertyKey)
  if (key === 'user') return sanitizeUser(child, visited)
  if (key === 'headers') return sanitizeHeaders(child, visited)
  if (key === 'tags') return sanitizeTags(child, visited)
  if (typeof child === 'string') {
    return Reflect.set(target, propertyKey, sanitizeText(child, key))
  }
  return !isObject(child) || sanitizeObject(child, visited, key)
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
    ? stripUrlParameters(withoutAbsoluteUrlParameters)
    : withoutAbsoluteUrlParameters

  const withoutBearerTokens = withoutRelativeUrlParameters.replace(BEARER_PATTERN, '[redacted]')
  const withoutJwtTokens = withoutBearerTokens.replace(JWT_PATTERN, '[redacted]')
  const withoutIpAddresses = withoutJwtTokens
    .replace(IPV4_CANDIDATE_PATTERN, redactIpv4Candidate)
    .replace(IPV6_CANDIDATE_PATTERN, (candidate) =>
      isIpv6Literal(candidate) ? '[redacted]' : candidate,
    )
  const withoutCredentialValues = CREDENTIAL_VALUE_PATTERNS.reduce(
    (sanitized, pattern) =>
      sanitized.replace(pattern, (_match, prefix: string) => `${prefix}[redacted]`),
    withoutIpAddresses,
  )

  return withoutCredentialValues.replace(EMAIL_PATTERN, '[redacted]').slice(0, MAX_STRING_LENGTH)
}

function sanitizeUrl(value: string): string {
  const { address, punctuation } = splitTrailingPunctuation(value)

  try {
    const url = new URL(address)
    return `${url.origin}${url.pathname}${punctuation}`
  } catch {
    return `${stripUrlParameters(address)}${punctuation}`
  }
}

function stripUrlParameters(value: string): string {
  let lastLineTerminator = -1
  for (let index = 0; index < value.length; index += 1) {
    if (LINE_TERMINATORS.has(value[index] ?? '')) lastLineTerminator = index
  }

  for (let index = lastLineTerminator + 1; index < value.length; index += 1) {
    const character = value[index]
    if (character === '?' || character === '#') return value.slice(0, index)
  }

  return value
}

function splitTrailingPunctuation(value: string): { address: string; punctuation: string } {
  let splitIndex = value.length
  while (splitIndex > 0 && URL_TRAILING_PUNCTUATION.has(value[splitIndex - 1] ?? '')) {
    splitIndex -= 1
  }

  return { address: value.slice(0, splitIndex), punctuation: value.slice(splitIndex) }
}

function redactIpv4Candidate(candidate: string): string {
  const octets = candidate.split('.')
  const isValid = octets.every((octet) => /^(0|[1-9]\d{0,2})$/.test(octet) && Number(octet) <= 255)

  return isValid ? '[redacted]' : candidate
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
