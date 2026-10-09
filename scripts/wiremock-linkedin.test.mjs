import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const mappingDirectory = new URL('../infra/wiremock/mappings/linkedin/', import.meta.url)
const readMapping = (name) => JSON.parse(readFileSync(new URL(name, mappingDirectory), 'utf8'))

const requestBodyPattern = (mapping) => mapping.request.bodyPatterns?.[0]?.matches
const javaRegex = (pattern) => new RegExp(pattern.replace(/^\(\?s\)/, ''), 's')

test('LinkedIn authorization-code exchange matches a whole form parameter only', () => {
  const mapping = readMapping('070-oauth-token.json')
  const pattern = javaRegex(requestBodyPattern(mapping))

  assert.equal(mapping.request.method, 'POST')
  assert.equal(mapping.request.urlPath, '/oauth/v2/accessToken')
  for (const body of ['grant_type=authorization_code', 'client_id=x&grant_type=authorization_code&code=y']) {
    assert.match(body, pattern)
  }
  for (const body of ['xgrant_type=authorization_code', 'grant_type=authorization_codex', 'grant_type=not_authorization_code']) {
    assert.doesNotMatch(body, pattern)
  }
})

test('LinkedIn refresh exchange matches a whole refresh_token form parameter only', () => {
  const mapping = readMapping('071-oauth-refresh-token.json')
  const pattern = javaRegex(requestBodyPattern(mapping))

  for (const body of ['grant_type=refresh_token', 'client_id=x&grant_type=refresh_token&refresh_token=y']) {
    assert.match(body, pattern)
  }
  for (const body of ['xgrant_type=refresh_token', 'grant_type=refresh_tokenx', 'grant_type=not_refresh_token']) {
    assert.doesNotMatch(body, pattern)
  }
})

test('LinkedIn rejects absent and unsupported OAuth grants without shadowing supported grants', () => {
  const mapping = readMapping('073-oauth-invalid-grant.json')
  const pattern = requestBodyPattern(mapping)
  const supportedGrants = ['authorization_code', 'refresh_token']
  const unsupportedBodies = [
    '',
    'client_id=local%2Fclient',
    'grant_type=',
    'grant_type=client_credentials',
    'grant_type=urn%3Aexample%3Acustom',
    'redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&grant_type=password&state=a%2Bb'
  ]

  assert.equal(mapping.priority, 11)
  assert.equal(mapping.request.method, 'POST')
  assert.equal(mapping.request.urlPath, '/oauth/v2/accessToken')
  assert.equal(mapping.response.status, 400)
  assert.match(pattern, /^\^\(\?!/)

  const javaCompatiblePattern = new RegExp(pattern)
  for (const body of unsupportedBodies) {
    assert.match(body, javaCompatiblePattern, `expected rejection matcher to accept ${body}`)
  }
  for (const grant of supportedGrants) {
    assert.doesNotMatch(`grant_type=${grant}&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback`, javaCompatiblePattern)
  }
})

test('LinkedIn browser authorization accepts the representative callback URI', () => {
  const mapping = readMapping('072-oauth-authorization.json')
  const callbackMatcher = new RegExp(mapping.request.queryParameters.redirect_uri.matches)

  assert.equal(mapping.request.method, 'GET')
  assert.equal(mapping.request.urlPath, '/oauth/v2/authorization')
  assert.match('http://localhost:5173/integrations/linkedin/callback', callbackMatcher)
})

test('LinkedIn authorization redirect template inserts code and state before fragments', () => {
  const mapping = readMapping('072-oauth-authorization.json')
  const location = mapping.response.headers.Location

  assert.match(location, /code=local-wiremock-authorization-code/)
  assert.match(location, /state=\{\{\{request\.query\.state\}\}\}/)
  assert.match(location, /#.*code=|#.*state=/)
})

test('LinkedIn mock upload accepts only the local upload paths returned by mappings', () => {
  const mapping = readMapping('020-binary-upload-success.json')

  assert.equal(mapping.request.method, 'PUT')
  assert.match(mapping.request.urlPathPattern, /^\/dms-uploads\/mock-\(image\|document\|video\)\/uploaded-/)
  assert.match(mapping.request.urlPathPattern, /\[0-9\]\+$/)
  assert.equal(mapping.request.headers['Content-Type'].matches, 'application/octet-stream')
})

test('development LinkedIn endpoints use worktree WireMock runtime configuration', () => {
  const contextModule = readFileSync(new URL('./worktree-context.mjs', import.meta.url), 'utf8')

  assert.match(contextModule, /WIREMOCK_HOST_PORT = context\.wiremockHostPort/)
  assert.match(contextModule, /SMP_LINKEDIN_API_BASE_URL: process\.env\.SMP_LINKEDIN_API_BASE_URL \|\| `http:\/\/localhost:\$\{context\.wiremockHostPort\}`/)
  assert.match(contextModule, /SMP_LINKEDIN_PUBLISHING_API_BASE_URL: process\.env\.SMP_LINKEDIN_PUBLISHING_API_BASE_URL \|\| `http:\/\/localhost:\$\{context\.wiremockHostPort\}`/)
  assert.match(contextModule, /SMP_LINKEDIN_AUTHORIZATION_BASE_URL: process\.env\.SMP_LINKEDIN_AUTHORIZATION_BASE_URL/)
  assert.match(contextModule, /SMP_LINKEDIN_TOKEN_BASE_URL: process\.env\.SMP_LINKEDIN_TOKEN_BASE_URL/)
})
