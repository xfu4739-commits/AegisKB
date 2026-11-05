import { legacyLinks } from '../../../config/environment.js'
export function legacyAdminLink(path) {
  const url = new URL(legacyLinks().admin)
  url.searchParams.set('redirect', path)
  return url.href
}
