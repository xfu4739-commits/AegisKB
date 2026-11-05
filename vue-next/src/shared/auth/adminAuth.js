import { ref } from 'vue'
import { authSession, tokenExpired } from './session.js'
import { http, HttpError } from '../api/http.js'

const api = {
  login: (credentials, signal) => http.json('/admin/auth/login', credentials, { auth: false, signal }),
  me: (signal) => http.json('/admin/auth/me', undefined, { method: 'GET', signal }),
  logout: (token) => http.json('/admin/auth/logout', {}, { auth: false, headers: { Authorization: `Bearer ${token}` } })
}

export function createAdminAuth(session, transport) {
  const busy = ref(false)
  let confirmation = null, confirmed = null, loginController = null, operation = 0, logoutWork = null
  async function confirm({ force = false, signal } = {}) {
    const snapshot = session.snapshot()
    if (!snapshot.token) return 'missing'
    if (tokenExpired(snapshot.token)) { session.clear(snapshot); return 'missing' }
    if (!force && confirmed && session.isCurrent(confirmed) && session.state.status === 'authenticated') return 'authenticated'
    if (confirmation && session.isCurrent(confirmation.snapshot)) return confirmation.work
    session.state.status = 'checking'; session.state.error = ''; session.state.profile = null
    const work = (async () => {
      try {
        const profile = await transport.me(signal)
        if (!session.isCurrent(snapshot)) return session.token() ? 'stale' : 'missing'
        if (typeof profile?.username !== 'string' || !profile.username.trim()) throw new HttpError('管理员信息格式不正确。', 502)
        session.confirm(profile, snapshot); confirmed = snapshot; return 'authenticated'
      } catch (error) {
        if (!session.isCurrent(snapshot)) return session.token() ? 'stale' : 'missing'
        if (error.status === 401) { session.clear(snapshot); return 'missing' }
        session.state.status = error.status === 403 ? 'forbidden' : 'error'
        session.state.error = error.message
        return session.state.status
      } finally { if (confirmation?.snapshot === snapshot) confirmation = null }
    })()
    confirmation = { snapshot, work }
    return work
  }
  async function login(credentials) {
    if (busy.value) return 'pending'
    busy.value = true; session.state.logoutError = ''
    const attempt = ++operation, before = session.snapshot()
    loginController = new AbortController()
    const signal = loginController.signal
    try {
      const result = await transport.login(credentials, signal)
      if (attempt !== operation || !session.isCurrent(before)) return 'stale'
      session.setToken(result?.token)
      return await confirm({ force: true, signal })
    } finally { if (attempt === operation) { busy.value = false; loginController = null } }
  }
  function cancelLogin() { operation++; loginController?.abort(); loginController = null; busy.value = false }
  function logout() {
    if (logoutWork && !session.token()) return logoutWork
    cancelLogin(); confirmed = null
    const token = session.token()
    // Capture the credential for this one logout request before clearing the browser.
    const request = token ? transport.logout(token) : Promise.resolve()
    session.clear()
    const cleared = session.snapshot()
    const work = request.catch(() => {
      if (session.isCurrent(cleared)) session.state.logoutError = '本机登录状态已清除，服务端退出请求失败。'
    }).finally(() => { if (logoutWork === work) logoutWork = null })
    logoutWork = work
    return work
  }
  return { confirm, login, logout, cancelLogin, busy }
}
export const adminAuth = createAdminAuth(authSession, api)
