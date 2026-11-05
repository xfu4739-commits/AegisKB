import { consumeStream } from './stream.js'
import { http } from '../../shared/api/http.js'
const post = (path, body) => http.json(path, body)

export const chatApi = {
  stream: (payload, onEvent) => http.stream('/api/chat/stream', payload, (body, isCurrent) => consumeStream(body, (event) => { if (isCurrent()) onEvent(event) })),
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
