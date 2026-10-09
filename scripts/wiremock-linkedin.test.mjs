import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const mappingDirectory = new URL('../infra/wiremock/mappings/linkedin/', import.meta.url)
const readMapping = (name) => JSON.parse(readFileSync(new URL(name, mappingDirectory), 'utf8'))

const requestBodyPattern = (mapping) => mapping.request.bodyPatterns?.[0]?.matches

test('LinkedIn authorization-code exchange matches only authorization_code grants', () => {
  const mapping = readMapping('070-oauth-token.json')

  assert.equal(mapping.request.method, 'POST')
  assert.equal(mapping.request.urlPath, '/oauth/v2/accessToken')
  assert.match(requestBodyPattern(mapping), /grant_type=authorization_code/)
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

test('LinkedIn browser authorization redirects to the supplied local callback with mock code and state', () => {
  const mapping = readMapping('072-oauth-authorization.json')

  assert.equal(mapping.request.method, 'GET')
  assert.equal(mapping.request.urlPath, '/oauth/v2/authorization')
  assert.match(mapping.request.queryParameters.redirect_uri.matches, /^http:\/\//)
  assert.match(mapping.response.headers.Location, /code=local-wiremock-authorization-code/)
  assert.match(mapping.response.headers.Location, /state=\{\{\{request\.query\.state\}\}\}/)
})

test('LinkedIn refresh exchange matches only refresh_token grants', () => {
  const mapping = readMapping('071-oauth-refresh-token.json')

  assert.equal(mapping.request.method, 'POST')
  assert.equal(mapping.request.urlPath, '/oauth/v2/accessToken')
  assert.match(requestBodyPattern(mapping), /grant_type=refresh_token/)
})

test('LinkedIn mock upload accepts only the local upload paths returned by mappings', () => {
  const mapping = readMapping('020-binary-upload-success.json')

  assert.equal(mapping.request.method, 'PUT')
  assert.match(mapping.request.urlPathPattern, /^\/dms-uploads\/mock-\(image\|document\|video\)\/uploaded-/)
  assert.match(mapping.request.urlPathPattern, /\[0-9\]\+$/)
  assert.equal(mapping.request.headers['Content-Type'].matches, 'application/octet-stream')
})

test('development LinkedIn endpoints point to the local WireMock on port 33185', () => {
  const development = readFileSync(new URL('../server/smp/src/main/resources/application-dev.yaml', import.meta.url), 'utf8')

  assert.match(development, /SMP_LINKEDIN_API_BASE_URL:http:\/\/localhost:33185/)
  assert.match(development, /SMP_LINKEDIN_PUBLISHING_API_BASE_URL:http:\/\/localhost:33185/)
  assert.match(development, /SMP_LINKEDIN_AUTHORIZATION_BASE_URL:http:\/\/localhost:33185\/oauth\/v2\/authorization/)
  assert.match(development, /SMP_LINKEDIN_TOKEN_BASE_URL:http:\/\/localhost:33185\/oauth\/v2\/accessToken/)
})
