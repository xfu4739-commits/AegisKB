import { afterEach, expect, it, vi } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { installAdminGuard, safeAdminReturn } from './adminGuard.js'
import { createAuthSession } from '../shared/auth/session.js'

const cleanups = []
const component = { render: () => null }
function fixture() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/chat', name: 'Chat', component },
    { path: '/admin/login', name: 'AdminLogin', component },
    { path: '/admin/access', name: 'AdminAccess', component },
    { path: '/admin', name: 'AdminHome', component, meta: { requiresAdminAuth: true, adminReturn: true } },
    { path: '/admin/:pathMatch(.*)*', name: 'AdminNotFound', component, meta: { requiresAdminAuth: true } }
  ] })
  const session = createAuthSession(localStorage), auth = { confirm: vi.fn().mockResolvedValue('authenticated') }
  cleanups.push(installAdminGuard(router, session, auth))
  return { router, session, auth }
}
afterEach(() => { cleanups.splice(0).forEach((dispose) => dispose()); localStorage.clear() })

it('only accepts registered, protected internal destinations and preserves their query/hash', () => {
  const { router } = fixture()
  expect(safeAdminReturn(router, '/admin?tab=account#name')).toBe('/admin?tab=account#name')
  for (const value of ['https://example.com', '//example.com', '/admin/login', '/admin/access', '/admin/not-implemented', '/administrator', '/chat', '/admin/%5cexample', '/admin/../chat', '/admin\n', ['/', '/admin']]) expect(safeAdminReturn(router, value)).toBe('/admin')
})
it('keeps public chat independent and redirects missing authentication safely', async () => {
  const { router, auth } = fixture()
  await router.push('/chat'); expect(auth.confirm).not.toHaveBeenCalled()
  await router.push('/admin?tab=account')
  expect(router.currentRoute.value.name).toBe('AdminLogin')
  expect(router.currentRoute.value.query.redirect).toBe('/admin?tab=account')
})
it.each(['error', 'forbidden'])('a %s result shows access failure without clearing credentials', async (status) => {
  const { router, session, auth } = fixture()
  session.setToken('fixture-token'); auth.confirm.mockResolvedValue(status)
  await router.push('/admin')
  expect(router.currentRoute.value.name).toBe('AdminAccess')
  expect(session.token()).toBe('fixture-token')
  await router.push('/admin/login')
  expect(router.currentRoute.value.name).toBe('AdminAccess')
})
it('a confirmed login rejects an external return URL and goes to the implemented home', async () => {
  const { router, session } = fixture(); session.setToken('fixture-token')
  await router.push('/admin/login?redirect=//example.com')
  expect(router.currentRoute.value.name).toBe('AdminHome')
})
it('session invalidation redirects protected pages, while unknown admin paths stay in admin', async () => {
  const { router, session, auth } = fixture(); session.setToken('fixture-token')
  await router.push('/admin/unknown'); expect(router.currentRoute.value.name).toBe('AdminNotFound')
  auth.confirm.mockResolvedValue('missing'); session.clear()
  await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('AdminLogin'))
  expect(router.currentRoute.value.query.redirect).toBe('/admin')
})
it('initial admin navigation rechecks the new identity when the original confirmation becomes stale', async () => {
  const { router, session, auth } = fixture(); session.setToken('new-token')
  auth.confirm.mockResolvedValueOnce('stale').mockResolvedValueOnce('authenticated')
  await router.push('/admin')
  expect(router.currentRoute.value.name).toBe('AdminHome')
  expect(auth.confirm).toHaveBeenCalledTimes(2)
})
it('an obsolete confirmation cannot override a later public navigation', async () => {
  const { router, session, auth } = fixture(); session.setToken('fixture-token')
  let finish
  auth.confirm.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
  const oldNavigation = router.push('/admin/login?redirect=/admin')
  await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
  await router.push('/chat')
  finish('authenticated'); await oldNavigation
  expect(router.currentRoute.value.name).toBe('Chat')
})
it('returning to the current public route also cancels an older login redirect', async () => {
  const { router, session, auth } = fixture(); session.setToken('fixture-token')
  await router.push('/chat')
  let finish
  auth.confirm.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
  const oldNavigation = router.push('/admin/login?redirect=/admin')
  await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
  await router.push('/chat')
  finish('authenticated'); await oldNavigation
  expect(router.currentRoute.value.name).toBe('Chat')
})
