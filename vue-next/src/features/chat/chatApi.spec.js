import { afterEach, expect, it, vi } from 'vitest'
import { chatApi } from './chatApi.js'
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear() })
it('streams the exact payload, carries auth and rejects JSON fallback responses', async () => {
  localStorage.setItem('aegis-kb-admin-token', 'isolated-token')
  const fetch = vi.fn().mockResolvedValueOnce(new Response('data: {"type":"text","content":"内容"}\n\n', { headers: { 'content-type': 'text/event-stream' } })).mockResolvedValueOnce(new Response('{"message":"服务未就绪"}', { headers: { 'content-type': 'application/json' } }))
  vi.stubGlobal('fetch', fetch)
  const callback = vi.fn(), payload = { conversationId: '90071992547409931234', question: '问题' }
  await chatApi.stream(payload, callback).done
  expect(callback).toHaveBeenCalledWith({ type: 'text', content: '内容' })
  expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer isolated-token')
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(payload)
  await expect(chatApi.stream(payload, callback).done).rejects.toThrow('服务未就绪')
})
it('uses string pagination and ID bodies without inventing a second protocol', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: '0', data: { sessions: [{ conversationId: '90071992547409931234' }], totalPages: '2' } }), { headers: { 'content-type': 'application/json' } })).mockResolvedValueOnce(new Response(JSON.stringify({ code: '0', data: { sessions: [], totalPages: '2' } }), { headers: { 'content-type': 'application/json' } }))
  vi.stubGlobal('fetch', fetch)
  expect((await chatApi.list())[0].conversationId).toBe('90071992547409931234')
  expect(JSON.parse(fetch.mock.calls[1][1].body).pageNo).toBe('2')
})
it('rejects business failures and clears expired authorization without fake data', async () => {
  localStorage.setItem('aegis-kb-admin-token', 'expired')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '401', message: '请重新登录' }), { status: 401, headers: { 'content-type': 'application/json' } })))
  await expect(chatApi.detail('large-string-id')).rejects.toThrow('请重新登录')
  expect(localStorage.getItem('aegis-kb-admin-token')).toBeNull()
})

it.each([
  ['knowledgeBases', [], '/api/chat/knowledge-base/options', {}],
  ['documents', [], '/api/chat/document/options', {}],
  ['detail', ['90071992547409931234'], '/api/chat/session/detail', { conversationId: '90071992547409931234' }],
  ['remove', ['90071992547409931234'], '/api/chat/session/reset', { conversationId: '90071992547409931234' }],
  ['stop', ['90071992547409931234'], '/api/chat/session/stop', { conversationId: '90071992547409931234' }],
  ['retrieval', ['conversation', '90071992547409931235'], '/api/chat/exchange/retrieval/results', { conversationId: 'conversation', exchangeId: '90071992547409931235' }]
])('preserves the %s wire contract', async (name, args, path, body) => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '0', data: [] }), { headers: { 'content-type': 'application/json' } }))
  vi.stubGlobal('fetch', fetch)
  await chatApi[name](...args)
  expect(fetch.mock.calls[0][0]).toBe(path)
  expect(fetch.mock.calls[0][1].method).toBe('POST')
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(body)
})

it('a late 401 from the previous token cannot clear a newer login', async () => {
  localStorage.setItem('aegis-kb-admin-token', 'old-token')
  let finish
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise((resolve) => { finish = resolve })))
  const pending = chatApi.detail('conversation')
  localStorage.setItem('aegis-kb-admin-token', 'new-token')
  finish(new Response('{"message":"旧登录已失效"}', { status: 401, headers: { 'content-type': 'application/json' } }))
  await expect(pending).rejects.toThrow()
  expect(localStorage.getItem('aegis-kb-admin-token')).toBe('new-token')
})
