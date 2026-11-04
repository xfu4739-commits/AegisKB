export function legacyLinks(value = import.meta.env.VITE_LEGACY_APP_URL) {
  let base
  try {
    base = new URL(value)
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password) throw new Error('Invalid legacy URL')
  } catch {
    base = new URL('http://localhost:5173')
  }
  base.search = ''
  base.hash = ''
  base.pathname = `${base.pathname.replace(/\/$/, '')}/`
  return {
    chat: new URL('chat', base).href,
    admin: new URL('admin/login?redirect=/admin/dashboard', base).href
  }
}
