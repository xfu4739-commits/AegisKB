import { authSession } from '../auth/session.js'

export class HttpError extends Error {
  constructor(message, status = 0, code = '') { super(message); this.name = 'HttpError'; this.status = status; this.code = code }
}
const cancelled = () => Object.assign(new Error('请求已取消。'), { name: 'AbortError' })

export function createHttpClient(session = authSession, fetcher = (...args) => fetch(...args)) {
  async function execute(path, options = {}) {
    const snapshot = session.snapshot()
    const controller = options.controller || new AbortController()
    const headers = { ...(options.auth !== false && snapshot.token ? { Authorization: `Bearer ${snapshot.token}` } : {}), ...options.headers }
    let body
    if (options.form) body = options.form
    else if (options.json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(options.json) }
    if (options.stream) headers.Accept = 'text/event-stream'
    const abort = () => controller.abort()
    options.signal?.addEventListener('abort', abort, { once: true })
    if (options.signal?.aborted) abort()
    const untrack = options.auth === false ? () => {} : session.track(controller, snapshot)
    let timedOut = false
    const timeout = options.timeout ?? (options.stream ? 0 : 30000)
    const timer = timeout ? setTimeout(() => { timedOut = true; controller.abort() }, timeout) : null
    try {
      if (controller.signal.aborted) throw cancelled()
      const response = await fetcher(path, { method: options.method || 'POST', headers, ...(body === undefined ? {} : { body }), signal: controller.signal })
      if (options.auth !== false && snapshot.token && !session.isCurrent(snapshot)) throw cancelled()
      const contentType = response.headers.get('content-type') || ''
      if (options.stream && response.ok && contentType.includes('text/event-stream') && response.body) {
        await options.stream(response.body, () => !controller.signal.aborted && (!snapshot.token || session.isCurrent(snapshot))); return
      }
      if (options.blob && response.ok) {
        const blob = await response.blob()
        const disposition = response.headers.get('content-disposition') || ''
        const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
        const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1]
        let filename = plain || 'download'
        try { if (encoded) filename = decodeURIComponent(encoded) } catch { /* keep safe fallback */ }
        if (options.auth !== false && snapshot.token && !session.isCurrent(snapshot)) throw cancelled()
        return { blob, fileName: filename.replace(/[\\/]/g, '_'), contentType: contentType || blob.type || 'application/octet-stream' }
      }
      const payload = contentType.includes('application/json') ? await response.json() : null
      const status = response.status === 401 || String(payload?.code) === '401' ? 401
        : response.status === 403 || String(payload?.code) === '403' ? 403 : response.status
      if (status === 401 && options.auth !== false) session.clear(snapshot)
      if (!response.ok || status === 401 || status === 403) throw new HttpError(payload?.message || `请求失败（${status}）`, status, payload?.code)
      if (!payload || options.stream) throw new HttpError(payload?.message || '服务返回了无法识别的响应，请检查后端连接。', 502)
      if (String(payload.code) !== '0') throw new HttpError(payload.message || '请求失败，请重试。', status, payload.code)
      if (options.auth !== false && snapshot.token && !session.isCurrent(snapshot)) throw cancelled()
      return payload.data
    } catch (error) {
      if (timedOut) throw new HttpError('请求超时，请重试。', 408)
      if (error.name === 'AbortError') throw cancelled()
      if (error instanceof HttpError) throw error
      throw new HttpError('连接失败，请检查后端服务。', 0)
    } finally {
      clearTimeout(timer); untrack(); options.signal?.removeEventListener('abort', abort)
    }
  }
  return {
    json: (path, json, options = {}) => execute(path, { ...options, json }),
    multipart: (path, form, options = {}) => execute(path, { ...options, form }),
    download: (path, json, options = {}) => execute(path, { ...options, json, blob: true }),
    stream(path, json, consume, options = {}) {
      const controller = new AbortController()
      return { controller, done: execute(path, { ...options, json, controller, stream: consume }) }
    }
  }
}
export const http = createHttpClient()
