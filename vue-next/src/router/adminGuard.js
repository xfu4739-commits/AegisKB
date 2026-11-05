import { watch } from 'vue'
import { isNavigationFailure, NavigationFailureType } from 'vue-router'

export function safeAdminReturn(router, value) {
  if (typeof value !== 'string' || !/^\/admin(?:\/|[?#]|$)/.test(value) || /[\\\u0000-\u0020]/.test(value)) return '/admin'
  try {
    const url = new URL(value, 'https://internal.invalid')
    if (url.origin !== 'https://internal.invalid') return '/admin'
    const decoded = decodeURIComponent(url.pathname)
    if (/[\\\u0000-\u0020]/.test(decoded)) return '/admin'
    const route = router.resolve(`${url.pathname}${url.search}${url.hash}`)
    return route.meta.adminReturn === true && route.name !== 'AdminNotFound' ? route.fullPath : '/admin'
  } catch { return '/admin' }
}

export function installAdminGuard(router, session, auth) {
  let navigation = 0
  const loginTarget = (path) => ({ name: 'AdminLogin', query: { redirect: safeAdminReturn(router, path) } })
  const removeGuard = router.beforeEach(async (to, from) => {
    const owner = ++navigation
    if (!to.meta.requiresAdminAuth && to.name !== 'AdminLogin') return true
    if (!session.token()) return to.meta.requiresAdminAuth ? loginTarget(to.fullPath) : true
    let result = await auth.confirm({ force: !from.meta.requiresAdminAuth && from.name !== 'AdminLogin' })
    if (owner !== navigation) return false
    while (result === 'stale') {
      result = await auth.confirm({ force: true })
      if (owner !== navigation) return false
    }
    if (result === 'authenticated') return to.name === 'AdminLogin' ? safeAdminReturn(router, to.query.redirect) : true
    if (to.name === 'AdminLogin') return result === 'missing' ? true : { name: 'AdminAccess', query: { redirect: safeAdminReturn(router, to.query.redirect) } }
    return result === 'missing' ? loginTarget(to.fullPath) : { name: 'AdminAccess', query: { redirect: safeAdminReturn(router, to.fullPath) } }
  })
  // Duplicate navigations skip beforeEach, but still express a newer user intent.
  const removeAfter = router.afterEach((_to, _from, failure) => {
    if (isNavigationFailure(failure, NavigationFailureType.duplicated)) navigation++
  })
  const unwatch = watch(() => session.state.epoch, () => {
    const route = router.currentRoute.value
    if (route.meta.requiresAdminAuth && !session.token()) router.replace(loginTarget(route.fullPath))
  })
  const storageChanged = (event) => {
    if (event.storageArea && event.storageArea !== localStorage) return
    if (event.key === null || event.key === 'aegis-kb-admin-token') {
      session.sync()
      if (router.currentRoute.value.meta.requiresAdminAuth) router.replace(loginTarget(router.currentRoute.value.fullPath))
    }
  }
  window.addEventListener('storage', storageChanged)
  return () => { removeGuard(); removeAfter(); unwatch(); window.removeEventListener('storage', storageChanged) }
}
