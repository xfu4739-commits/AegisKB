import { allowReadSnapshot } from './admin-inventory.js'

export const documentId = '90071992547409931234'
export const documentRecord = {
  documentId, documentName: 'A0 隔离示例 · 团队知识使用指南', originalFileName: 'team-guide.pdf',
  knowledgeBaseId: 'a0-kb', knowledgeBaseName: 'A0 隔离知识库', fileTypeName: 'PDF', fileSize: '245760',
  parseStatus: '3', strategyStatus: '3', indexStatus: '3', parseStatusName: '解析成功',
  strategyStatusName: '策略确认', indexStatusName: '索引完成', editTime: '2026-11-04T09:00:00+08:00',
  latestTaskId: null, taskHistory: []
}
const session = {
  conversationId: 'a0-session', latestQuestion: '如何维护团队知识？', latestAnswer: '定期核对知识来源和使用范围。',
  latestExchangeId: '90071992547409931235', latestTurnStatus: 'COMPLETED', chatMode: 'OPEN_CHAT', running: false,
  messageCount: '2', updatedAt: '2026-11-04T09:00:00+08:00',
  exchanges: [{ exchangeId: '90071992547409931235', question: '如何维护团队知识？', answer: '定期核对知识来源和使用范围。', status: 'COMPLETED', references: [], stageTraces: [] }]
}
const kb = { id: 'a0-kb', baseName: 'A0 隔离知识库', description: '仅用于界面对照，不写入真实服务。', documentCount: '1', vectorModel: 'fixture-model', status: '1', editTime: '2026-11-04T09:00:00+08:00' }
export const configSnapshot = {
  configVersion: 6, sourceType: 'DATABASE', categories: [{
    groupKey: 'retrieval', groupLabel: '检索与排序', categoryKey: 'relevance', categoryLabel: '相关性阈值',
    description: 'A0 隔离配置快照', items: [{ configKey: 'ragRuntime.minVectorSimilarity', label: '向量最低相似度',
      description: '低于该比例的候选不进入融合。', value: 0.45, valueType: 'DECIMAL', controlType: 'PERCENTAGE',
      minValue: 0, maxValue: 1, step: 0.01, displayScale: 100, unit: '%', effectiveModeLabel: '新会话生效' }]
  }]
}

const snapshots = {
  '/admin/auth/me': { username: 'A0-fixture-admin' },
  '/manage/knowledge/base/list': [kb],
  '/manage/knowledge/base/detail': kb,
  '/manage/document/page/query': { records: [documentRecord], pageNo: '1', pageSize: '12', total: '1', totalPages: '1' },
  '/manage/document/detail/query': documentRecord,
  '/manage/document/strategy/plan/query': null,
  '/manage/document/index/build/progress/query': null,
  '/manage/document/parse-route/progress/query': null,
  '/manage/document/task/log/query': { logs: [], total: '0' },
  '/manage/document/chunk/query': { records: [], parentBlocks: [], total: '0', pageNo: '1', pageSize: '20' },
  '/manage/document/rag/snapshot/query': null,
  '/manage/knowledge/route/trace/page/query': { records: [], pageNo: '1', total: '0', totalPages: '0' },
  '/manage/config/current/query': configSnapshot,
  '/manage/config/history/page/query': { records: [], pageNo: 1, pageSize: 10, total: 0, totalPages: 0 },
  '/api/chat/session/list': { sessions: [session], pageNo: '1', pageSize: '12', totalSize: '1', totalPages: '1' },
  '/api/chat/session/detail': session,
  '/api/chat/exchange/detail': session.exchanges[0],
  '/api/chat/exchange/retrieval/results': [],
  '/api/chat/exchange/channel/executions': [],
  '/api/chat/stage/benchmarks': []
}

export async function mockLegacyAdmin(context, { authenticated = true } = {}) {
  const calls = [], blocked = []
  if (authenticated) {
    // Fictional browser-only token, never sent to any real service.
    const payload = Buffer.from(JSON.stringify({ sub: 'a0-fixture', exp: 4102444800 })).toString('base64url')
    await context.addInitScript(({ token }) => {
      if (location.origin !== 'http://localhost:5173') return
      localStorage.setItem('aegis-kb-admin-token', token)
      localStorage.setItem('aegis-kb-admin-user', 'A0-fixture-admin')
    }, { token: `fixture.${payload}.not-a-real-signature` })
  }
  await context.route((url) => /^\/(?:api|manage|admin\/auth)\//.test(url.pathname), async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname
    try {
      allowReadSnapshot(request.method(), path)
      if (!Object.hasOwn(snapshots, path)) throw new Error(`Missing snapshot: ${path}`)
      calls.push({ method: request.method(), path, body: request.postDataJSON() })
      await route.fulfill({ json: { code: '0', data: snapshots[path] } })
    } catch (error) {
      blocked.push(String(error.message))
      await route.abort('blockedbyclient')
    }
  })
  return { calls, blocked }
}
