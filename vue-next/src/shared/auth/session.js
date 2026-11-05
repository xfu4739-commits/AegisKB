import { reactive } from 'vue'

export const TOKEN_KEY = 'aegis-kb-admin-token'
export const USER_KEY = 'aegis-kb-admin-user'

export function tokenExpired(token, now = Date.now()) {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(part))
    return typeof payload.exp === 'number' && now >= payload.exp * 1000
  } catch { return false } // Opaque/malformed credentials still require server verification.
}

export function createAuthSession(storage = localStorage) {
  const state = reactive({ epoch: 0, status: 'anonymous', profile: null, error: '', logoutError: '' })
  const requests = new Set()
  const token = () => storage.getItem(TOKEN_KEY) || ''
  const snapshot = () => ({ token: token(), epoch: state.epoch })
  const isCurrent = (value) => value.token === token() && value.epoch === state.epoch
  function resetRequests() {
    for (const controller of requests) controller.abort()
    requests.clear()
  }
  function changed() {
    state.profile = null; state.status = 'anonymous'; state.error = ''
    state.epoch++; resetRequests()
  }
  return {
    state, token, snapshot, isCurrent,
    setToken(value) {
      if (typeof value !== 'string' || !value.trim()) throw new Error('登录响应缺少 token。')
      storage.setItem(TOKEN_KEY, value); storage.removeItem(USER_KEY); changed()
    },
    clear(expected) {
      if (expected && !isCurrent(expected)) return false
      storage.removeItem(TOKEN_KEY); storage.removeItem(USER_KEY); changed(); return true
    },
    confirm(profile, expected) {
      if (!isCurrent(expected)) return false
      state.profile = { username: profile.username }; state.status = 'authenticated'; state.error = ''
      storage.setItem(USER_KEY, profile.username); return true
    },
    track(controller, expected) {
      if (!expected.token) return () => {}
      if (!isCurrent(expected)) controller.abort()
      else requests.add(controller)
      return () => requests.delete(controller)
    },
    sync: changed
  }
}

export const authSession = createAuthSession()
