import { consumeStream } from './stream.js'

function headers() {
  const token = localStorage.getItem('aegis-kb-admin-token')
  return { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }
}
function checkAuth(response) {
  if (response.status === 401) {
    localStorage.removeItem('aegis-kb-admin-token'); localStorage.removeItem('aegis-kb-admin-user')
  }
}
async function post(path, body) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 30000)
  try {
    const response = await fetch(path, { method: 'POST', headers: headers(), body: JSON.stringify(body), signal: controller.signal })
    checkAuth(response)
    if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('服务返回了无法识别的响应，请检查后端连接。')
    const payload = await response.json()
    if (!response.ok || String(payload?.code) !== '0') throw new Error(payload?.message || `请求失败（${response.status}）`)
    return payload.data
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('请求超时，请重试。')
    throw error
  } finally { clearTimeout(timeout) }
}

export const chatApi = {
  stream: (payload, onEvent) => {
    const controller = new AbortController()
    const done = (async () => {
      const response = await fetch('/api/chat/stream', { method: 'POST', headers: { ...headers(), Accept: 'text/event-stream' }, body: JSON.stringify(payload), signal: controller.signal })
      checkAuth(response)
      if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream') || !response.body) {
        const body = await response.json().catch(() => null)
        throw new Error(body?.message || `无法开始流式回答（${response.status}），请检查后端连接。`)
      }
      await consumeStream(response.body, onEvent)
    })()
    return { controller, done }
  },
  stop: (conversationId) => post('/api/chat/session/stop', { conversationId }),
  retrieval: (conversationId, exchangeId) => post('/api/chat/exchange/retrieval/results', { conversationId, exchangeId: String(exchangeId) }),
  list: async () => {
    const sessions = []
    for (let page = 1; ; page++) {
      const data = await post('/api/chat/session/list', { keyword: '', chatMode: 'ALL', turnStatus: 'ALL', pageNo: String(page), pageSize: '200' })
      if (!Array.isArray(data?.sessions)) throw new Error('会话列表格式不正确。')
      sessions.push(...data.sessions)
      if (page >= Number(data.totalPages || 1)) return sessions
    }
  },
  knowledgeBases: () => post('/api/chat/knowledge-base/options', {}),
  documents: () => post('/api/chat/document/options', {}),
  detail: (conversationId) => post('/api/chat/session/detail', { conversationId }),
  remove: (conversationId) => post('/api/chat/session/reset', { conversationId })
}
