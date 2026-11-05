import { afterEach, describe, expect, it, vi } from 'vitest'
import { createAuthSession, tokenExpired } from './session.js'
import { createAdminAuth } from './adminAuth.js'
import { createHttpClient, HttpError } from '../api/http.js'

const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture() {
  const values = new Map()
  const storage = { getItem: (key) => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }
  const session = createAuthSession(storage)
  const api = { login: vi.fn().mockResolvedValue({ token: 'new-token', username: 'untrusted-login-name' }), me: vi.fn().mockResolvedValue({ username: 'verified-admin' }), logout: vi.fn().mockResolvedValue(null) }
  return { session, storage, api, auth: createAdminAuth(session, api) }
}
const json = (data, status = 200, code = '0') => new Response(JSON.stringify({ code, data, message: status === 401 ? '登录过期' : '请求失败' }), { status, headers: { 'content-type': 'application/json' } })
afterEach(() => vi.useRealTimers())

describe('A1 authentication ownership', () => {
  it('installs the server /me profile and never treats the login username as authority', async () => {
    const { session, auth, api } = fixture()
    expect(await auth.login({ username: 'fixture', password: 'isolated-password' })).toBe('authenticated')
    expect(session.state.profile).toEqual({ username: 'verified-admin' })
    expect(api.me).toHaveBeenCalledTimes(1)
    expect(await auth.confirm()).toBe('authenticated')
    expect(api.me).toHaveBeenCalledTimes(1)
  })
  it('deduplicates concurrent session checks and refuses a malformed profile', async () => {
    const { session, auth, api } = fixture(), pending = deferred()
    session.setToken('stored-token'); api.me.mockReturnValue(pending.promise)
    const checks = [auth.confirm(), auth.confirm()]
    expect(api.me).toHaveBeenCalledTimes(1)
    pending.resolve({ username: '' })
    expect(await Promise.all(checks)).toEqual(['error', 'error'])
    expect(session.state.profile).toBeNull()
    expect(session.token()).toBe('stored-token')
  })
  it('a failed login cannot replace an existing credential; repeated submission sends once', async () => {
    const { session, auth, api } = fixture(), pending = deferred()
    session.setToken('existing-token'); api.login.mockReturnValue(pending.promise)
    const first = auth.login({ username: 'fixture', password: 'isolated-password' })
    expect(await auth.login({ username: 'fixture', password: 'isolated-password' })).toBe('pending')
    expect(api.login).toHaveBeenCalledTimes(1)
    pending.reject(new HttpError('账号或密码不正确', 401))
    await expect(first).rejects.toThrow('账号或密码不正确')
    expect(session.token()).toBe('existing-token')
    expect(auth.busy.value).toBe(false)
  })
  it('distinguishes an unavailable server and forbidden response without deleting the token', async () => {
    const { session, auth, api } = fixture()
    session.setToken('stored-token'); api.me.mockRejectedValueOnce(new HttpError('无法连接', 0)).mockRejectedValueOnce(new HttpError('无权访问', 403))
    expect(await auth.confirm()).toBe('error'); expect(session.state.error).toBe('无法连接')
    expect(await auth.confirm()).toBe('forbidden'); expect(session.token()).toBe('stored-token')
    expect(session.state.profile).toBeNull()
  })
  it('logout aborts authenticated requests and late /me cannot restore the profile', async () => {
    const { session, auth, api } = fixture(), pending = deferred()
    session.setToken('stored-token'); api.me.mockReturnValue(pending.promise)
    const controller = new AbortController(); session.track(controller, session.snapshot())
    const check = auth.confirm(); await auth.logout()
    expect(controller.signal.aborted).toBe(true)
    pending.resolve({ username: 'old-admin' })
    expect(await check).toBe('missing')
    expect(session.token()).toBe(''); expect(session.state.profile).toBeNull()
  })
  it('leaving a login ignores its late response', async () => {
    const { session, auth, api } = fixture(), pending = deferred()
    api.login.mockReturnValue(pending.promise)
    const login = auth.login({ username: 'fixture', password: 'isolated-password' })
    auth.cancelLogin(); pending.resolve({ token: 'cancelled-login' })
    expect(await login).toBe('stale'); expect(session.token()).toBe('')
  })
  it('an older logout failure cannot affect a later login or prevent its logout', async () => {
    const { session, auth, api } = fixture(), pending = deferred()
    session.setToken('old-token'); api.logout.mockReturnValueOnce(pending.promise)
    const oldLogout = auth.logout(); await auth.login({ username: 'fixture', password: 'isolated-password' })
    pending.reject(new Error('old logout failure')); await oldLogout
    expect(session.token()).toBe('new-token'); expect(session.state.logoutError).toBe('')
    const second = deferred(); api.logout.mockReturnValueOnce(second.promise)
    const newLogout = auth.logout(); expect(session.token()).toBe('')
    second.resolve(); await newLogout
  })
  it('known expired JWT is rejected locally; opaque credentials still require /me', async () => {
    const { session, auth, api } = fixture()
    const token = `header.${btoa(JSON.stringify({ exp: 1 }))}.signature`
    expect(tokenExpired(token)).toBe(true); expect(tokenExpired('opaque-token')).toBe(false)
    session.setToken(token); expect(await auth.confirm()).toBe('missing')
    expect(api.me).not.toHaveBeenCalled()
  })
  it('a pending older logout does not suppress logout of a newer authenticated session', async () => {
    const { session, auth, api } = fixture(), pending = deferred()
    session.setToken('old-token'); api.logout.mockReturnValueOnce(pending.promise)
    const first = auth.logout()
    await auth.login({ username: 'fixture', password: 'isolated-password' })
    await auth.logout()
    expect(api.logout).toHaveBeenCalledTimes(2)
    expect(api.logout).toHaveBeenLastCalledWith('new-token')
    expect(session.token()).toBe('')
    pending.reject(new Error('old logout error')); await first
    expect(session.state.logoutError).toBe('')
  })
})

describe('A1 shared transport', () => {
  it('late responses and 401s cannot cross a new epoch, including reuse of the same token', async () => {
    const { session } = fixture(), first = deferred(), second = deferred()
    session.setToken('same-token')
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const client = createHttpClient(session, fetcher)
    const a = client.json('/read', {}), b = client.json('/read', {})
    session.setToken('same-token')
    first.resolve(json({ username: 'old' })); second.resolve(json(null, 401, '401'))
    await expect(a).rejects.toHaveProperty('name', 'AbortError')
    await expect(b).rejects.toHaveProperty('name', 'AbortError')
    expect(session.token()).toBe('same-token')
  })
  it('current 401 clears authentication; 403 preserves the credential', async () => {
    const { session } = fixture()
    session.setToken('stored-token')
    const client = createHttpClient(session, vi.fn().mockResolvedValueOnce(json(null, 403, '403')).mockResolvedValueOnce(json(null, 401, '401')))
    await expect(client.json('/read', {})).rejects.toHaveProperty('status', 403)
    expect(session.token()).toBe('stored-token')
    await expect(client.json('/read', {})).rejects.toHaveProperty('status', 401)
    expect(session.token()).toBe('')
  })
  it('multipart lets the browser create its boundary and preserves meta types; downloads can be JSON files', async () => {
    const { session } = fixture(); session.setToken('stored-token')
    const fetcher = vi.fn().mockResolvedValueOnce(json({ documentId: '90071992547409931234' })).mockResolvedValueOnce(new Response('{"documentId":"90071992547409931234"}', { headers: { 'content-type': 'application/json', 'content-disposition': "attachment; filename*=UTF-8''artifact.json" } }))
    const client = createHttpClient(session, fetcher), form = new FormData()
    form.append('file', new Blob(['fixture']), 'fixture.md'); form.append('meta', new Blob(['{"knowledgeBaseId":"large-id"}'], { type: 'application/json' }))
    expect(await client.multipart('/upload', form)).toEqual({ documentId: '90071992547409931234' })
    expect(fetcher.mock.calls[0][1].body).toBe(form)
    expect(fetcher.mock.calls[0][1].headers['Content-Type']).toBeUndefined()
    expect(fetcher.mock.calls[0][1].headers.Authorization).toBe('Bearer stored-token')
    const download = await client.download('/download', { documentId: '90071992547409931234' })
    expect(download.fileName).toBe('artifact.json'); expect(download.contentType).toBe('application/json')
    expect(await download.blob.text()).toContain('90071992547409931234')
  })
  it('times out bounded JSON requests and preserves explicit cancellation', async () => {
    vi.useFakeTimers()
    const { session } = fixture()
    const client = createHttpClient(session, (_path, { signal }) => new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(Object.assign(new Error('abort'), { name: 'AbortError' })))))
    const timed = expect(client.json('/read', {})).rejects.toHaveProperty('status', 408)
    await vi.advanceTimersByTimeAsync(30001); await timed
    const controller = new AbortController()
    const cancelled = expect(client.json('/read', {}, { signal: controller.signal })).rejects.toHaveProperty('name', 'AbortError')
    controller.abort(); await cancelled
  })
})
