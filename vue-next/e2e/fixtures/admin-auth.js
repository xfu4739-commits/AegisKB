export const fixtureToken = 'a1-fixture-token'
export async function mockAdminAuth(context) {
  const state = { loginFailures: 0, loginDelay: 0, loginCalls: 0, meCalls: 0, meStatus: 200, logoutCalls: 0, logoutStatus: 200, blocked: [], requests: [] }
  await context.route((url) => /^\/(?:api|manage|admin\/auth)\//.test(url.pathname), async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname
    state.requests.push({ path, method: request.method() })
    let data = null, status = 200, code = '0', message = ''
    if (path === '/admin/auth/login' && request.method() === 'POST') {
      state.loginCalls++
      const body = request.postDataJSON()
      if (state.loginDelay) await new Promise((resolve) => setTimeout(resolve, state.loginDelay))
      if (state.loginFailures > 0 || body.username !== 'fixture-admin' || body.password !== 'fixture-password') { state.loginFailures--; status = 401; code = '401'; message = '账号或密码不正确' }
      else data = { token: fixtureToken, username: 'login-display-name', expireMinutes: 30 }
    } else if (path === '/admin/auth/me' && request.method() === 'GET') {
      state.meCalls++
      status = request.headers().authorization === `Bearer ${fixtureToken}` ? state.meStatus : 401
      if (status === 200) data = { username: 'fixture-admin' }
      else { code = String(status); message = status === 403 ? '无权访问此后台' : status === 401 ? '请重新登录' : '认证服务暂不可用' }
    } else if (path === '/admin/auth/logout' && request.method() === 'POST') {
      state.logoutCalls++; status = state.logoutStatus; code = status === 200 ? '0' : String(status)
    } else if (['/manage/document/page/query', '/manage/knowledge/route/trace/page/query'].includes(path) && request.method() === 'POST') data = { records: [], total: '0' }
    else if (path === '/api/chat/session/list') data = { sessions: [], totalPages: '0', totalSize: '0', pageNo: '1', pageSize: '200' }
    else if (['/api/chat/knowledge-base/options', '/api/chat/document/options'].includes(path)) data = []
    else { state.blocked.push(`${request.method()} ${path}`); await route.abort('blockedbyclient'); return }
    await route.fulfill({ status, json: { code, data, message } })
  })
  return state
}

export async function loginAsFixture(page) {
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('fixture-admin')
  await page.getByLabel('密码', { exact: true }).fill('fixture-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
}
export async function seedAdmin(page, token = fixtureToken) {
  await page.goto('/chat')
  await page.getByRole('textbox', { name: '输入问题' }).waitFor()
  await page.evaluate((token) => localStorage.setItem('aegis-kb-admin-token', token), token)
  await page.goto('/admin/account')
}
