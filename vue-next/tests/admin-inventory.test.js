import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { adminPages, adminEndpoints, allowReadSnapshot, protocolCases } from '../e2e/fixtures/admin-inventory.js'

const readOld = (path) => readFileSync(new URL(`../../vue/src/${path}`, import.meta.url), 'utf8')

test('A8 unused knowledge-base wrappers have no legacy page consumer',()=>{
  const source=readOld('views/admin/AdminKnowledgeBaseView.vue')
  for(const name of ['queryKnowledgeBaseDetail','updateKnowledgeBaseConfig']) assert.doesNotMatch(source,new RegExp('manageApi\\.'+name+'\\('))
})

test('every registered legacy admin page and every manage/auth endpoint has a migration owner', () => {
  const router = readOld('router/index.js')
  const components = [...router.matchAll(/import\('\.\.\/views\/((?:admin\/)?Admin[^']+\.vue)'\)/g)].map((m) => m[1])
  assert.deepEqual(adminPages.map((p) => p.source).sort(), components.sort())
  const source = readOld('api/api.js')
  const endpoints = [...source.matchAll(/['"]((?:\/manage\/|\/admin\/auth\/)[^'"]+)['"]/g)].map((m) => m[1])
  assert.deepEqual(adminEndpoints.filter((e) => !e.path.startsWith('/api/')).map((e) => e.path).sort(), endpoints.sort())
  assert.equal(new Set(adminEndpoints.map((e) => e.path)).size, adminEndpoints.length)
  for (const item of [...adminPages, ...adminEndpoints]) assert.match(item.owner, /^A[1-7]$/)
})

test('snapshot transport fails closed for writes, unknown paths and incorrect HTTP methods', () => {
  for (const item of adminEndpoints.filter((e) => e.effect !== 'read')) {
    assert.throws(() => allowReadSnapshot(item.method, item.path), /Blocked/)
  }
  assert.throws(() => allowReadSnapshot('POST', '/manage/unknown/query'), /Blocked/)
  assert.throws(() => allowReadSnapshot('GET', '/manage/document/page/query'), /Blocked/)
  assert.equal(allowReadSnapshot('POST', '/manage/document/page/query').effect, 'read')
  assert.equal(allowReadSnapshot('GET', '/admin/auth/me').effect, 'read')
})

test('legacy numeric serialization preserves string identities, booleans and null recursively', () => {
  // Execute the current pure producer, without importing legacy application code.
  const source = readOld('api/api.js')
  const start = source.indexOf('function stringifyManageValue(')
  const end = source.indexOf('\nasync function parseJsonResponse', start)
  assert.ok(start >= 0 && end > start)
  const convert = runInNewContext(`${source.slice(start, end)}; stringifyManageValue`)
  assert.deepEqual(JSON.parse(JSON.stringify(convert(protocolCases.manage.input))), protocolCases.manage.wire)
  assert.equal(convert(90071992547409931234n), '90071992547409931234')
  // Config methods explicitly bypass that conversion. Protect the protocol distinction.
  for (const name of ['updateSystemConfigItem', 'querySystemConfigHistory', 'querySystemConfigHistoryDetail', 'restoreSystemConfigHistory']) {
    const method = source.slice(source.indexOf(`  ${name}(`)).split('\n  },')[0]
    assert.match(method, /body: payload/)
    assert.doesNotMatch(method, /stringifyManageValue/)
  }
})

test('long log cursor fixture reproduces the old precision loss; A4 must replace that behavior', () => {
  const source = readOld('features/admin/documentWorkflow.js')
  const start = source.indexOf('export function latestIncrementalLogId(')
  const end = source.indexOf('\nexport function createSubmissionGuard', start)
  const oldCursor = runInNewContext(`${source.slice(start, end).replace('export ', '')}; latestIncrementalLogId`)
  assert.notEqual(String(oldCursor([{ id: protocolCases.logId }])), protocolCases.logId)
  assert.equal(typeof protocolCases.logId, 'string')
})
