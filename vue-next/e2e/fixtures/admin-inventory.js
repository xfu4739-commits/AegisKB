// A0 inventory: test evidence only. Never imported by application code.
export const adminPages = [
  ['/admin/login', 'AdminLoginView.vue', 'A1'],
  ['/admin', 'admin/AdminLayoutView.vue', 'A1'],
  ['/admin/dashboard', 'admin/AdminDashboardView.vue', 'A2'],
  ['/admin/quality-overview', 'admin/AdminQualityOverviewView.vue', 'A2'],
  ['/admin/knowledge-bases', 'admin/AdminKnowledgeBaseView.vue', 'A3'],
  ['/admin/documents', 'admin/AdminDocumentListView.vue', 'A3'],
  ['/admin/documents/:documentId', 'admin/AdminDocumentDetailView.vue', 'A4'],
  ['/admin/knowledge-route', 'admin/AdminKnowledgeRouteView.vue', 'A5'],
  ['/admin/knowledge-route/traces', 'admin/AdminKnowledgeRouteTraceView.vue', 'A5'],
  ['/admin/observability', 'admin/AdminObservabilityListView.vue', 'A6'],
  ['/admin/observability/:conversationId', 'admin/AdminObservabilitySessionView.vue', 'A6'],
  ['/admin/observability/:conversationId/exchanges/:exchangeId', 'admin/AdminObservabilityDetailView.vue', 'A6'],
  ['/admin/settings/configuration', 'admin/AdminSystemConfigView.vue', 'A7']
].map(([path, source, owner]) => ({ path, source, owner }))

// Explicit classifications; a POST can be read-only. Never infer safety from a URL suffix.
export const adminEndpoints = [
  ['login', '/admin/auth/login', 'auth', 'A1'],
  ['logout', '/admin/auth/logout', 'auth', 'A1'],
  ['currentUser', '/admin/auth/me', 'read', 'A1'],
  ['uploadDocument', '/manage/document/upload', 'write', 'A3'],
  ['saveKnowledgeBase', '/manage/knowledge/base/save', 'write', 'A3'],
  ['deleteKnowledgeBase', '/manage/knowledge/base/delete', 'write', 'A3'],
  ['listKnowledgeBases', '/manage/knowledge/base/list', 'read', 'A3'],
  ['queryKnowledgeBaseDetail', '/manage/knowledge/base/detail', 'read', 'A3'],
  ['updateKnowledgeBaseConfig', '/manage/knowledge/base/config/update', 'write', 'A3'],
  ['queryDocumentPage', '/manage/document/page/query', 'read', 'A3'],
  ['queryDocumentDetail', '/manage/document/detail/query', 'read', 'A4'],
  ['deleteDocument', '/manage/document/delete', 'write', 'A3'],
  ['queryStrategyPlan', '/manage/document/strategy/plan/query', 'read', 'A4'],
  ['confirmStrategy', '/manage/document/strategy/confirm', 'write', 'A4'],
  ['buildIndex', '/manage/document/index/build', 'write', 'A4'],
  ['queryIndexBuildProgress', '/manage/document/index/build/progress/query', 'read', 'A4'],
  ['queryParseRouteProgress', '/manage/document/parse-route/progress/query', 'read', 'A4'],
  ['queryParseArtifacts', '/manage/document/parse-artifact/query', 'read', 'A4'],
  ['queryParseArtifactContent', '/manage/document/parse-artifact/content/query', 'read', 'A4'],
  ['downloadParseArtifact', '/manage/document/parse-artifact/download', 'read', 'A4'],
  ['queryDocumentChunks', '/manage/document/chunk/query', 'read', 'A4'],
  ['queryDocumentChunkDetail', '/manage/document/chunk/detail/query', 'read', 'A4'],
  ['queryDocumentRagSnapshot', '/manage/document/rag/snapshot/query', 'read', 'A4'],
  ['queryDocumentRagParserDiagnostic', '/manage/document/rag/parser-diagnostic/query', 'read', 'A4'],
  ['queryDocumentRagPageOverlayIndex', '/manage/document/rag/page-overlay/index/query', 'read', 'A4'],
  ['queryDocumentRagPageOverlayDetail', '/manage/document/rag/page-overlay/detail/query', 'read', 'A4'],
  ['queryDocumentRagArtifactNodes', '/manage/document/rag/artifact/node/page/query', 'read', 'A4'],
  ['queryDocumentRagArtifactNodeDetail', '/manage/document/rag/artifact/node/detail/query', 'read', 'A4'],
  ['queryDocumentRagArtifactRelations', '/manage/document/rag/artifact/relation/page/query', 'read', 'A4'],
  ['queryDocumentRagArtifactGraphWindow', '/manage/document/rag/artifact/graph/window/query', 'read', 'A4'],
  ['queryDocumentRagArtifactTableWindow', '/manage/document/rag/artifact/table/window/query', 'read', 'A4'],
  ['queryTaskLogs', '/manage/document/task/log/query', 'read', 'A4'],
  ['saveKnowledgeScope', '/manage/knowledge/scope/save', 'write', 'A5'],
  ['deleteKnowledgeScope', '/manage/knowledge/scope/delete', 'write', 'A5'],
  ['listKnowledgeScopes', '/manage/knowledge/scope/list', 'read', 'A5'],
  ['saveKnowledgeTopic', '/manage/knowledge/topic/save', 'write', 'A5'],
  ['deleteKnowledgeTopic', '/manage/knowledge/topic/delete', 'write', 'A5'],
  ['listKnowledgeTopics', '/manage/knowledge/topic/list', 'read', 'A5'],
  ['queryDocumentProfile', '/manage/knowledge/document/profile/detail', 'read', 'A5'],
  ['regenerateDocumentProfile', '/manage/knowledge/document/profile/regenerate', 'write', 'A5'],
  ['batchRegenerateDocumentProfiles', '/manage/knowledge/document/profile/batch/regenerate', 'write', 'A5'],
  ['listTopicDocuments', '/manage/knowledge/topic/document/list', 'read', 'A5'],
  ['saveTopicDocumentRelation', '/manage/knowledge/topic/document/save', 'write', 'A5'],
  ['removeTopicDocumentRelation', '/manage/knowledge/topic/document/remove', 'write', 'A5'],
  ['queryKnowledgeRouteTracePage', '/manage/knowledge/route/trace/page/query', 'read', 'A5'],
  ['queryQualityOverview', '/manage/observability/quality/overview/query', 'read', 'A2'],
  ['querySystemConfigCurrent', '/manage/config/current/query', 'read', 'A7'],
  ['updateSystemConfigItem', '/manage/config/item/update', 'write', 'A7'],
  ['querySystemConfigHistory', '/manage/config/history/page/query', 'read', 'A7'],
  ['querySystemConfigHistoryDetail', '/manage/config/history/detail/query', 'read', 'A7'],
  ['restoreSystemConfigHistory', '/manage/config/history/restore', 'write', 'A7'],
  ['listSessionsPage', '/api/chat/session/list', 'read', 'A6'],
  ['getSession', '/api/chat/session/detail', 'read', 'A6'],
  ['getExchangeDetail', '/api/chat/exchange/detail', 'read', 'A6'],
  ['getRetrievalResults', '/api/chat/exchange/retrieval/results', 'read', 'A6'],
  ['getChannelExecutions', '/api/chat/exchange/channel/executions', 'read', 'A6'],
  ['getStageBenchmarks', '/api/chat/stage/benchmarks', 'read', 'A6'],
  ['rebuildConversationSummary', '/api/chat/session/summary/rebuild', 'write', 'A6']
].map(([name, path, effect, owner]) => ({
  name, path, effect, owner,
  method: path === '/admin/auth/me' ? 'GET' : 'POST',
  request: path.endsWith('/upload') ? 'multipart:file+meta'
    : path.startsWith('/manage/') && !path.startsWith('/manage/config/') ? 'manage-recursive-numeric-strings' : 'json-original-types',
  response: path.endsWith('/download') ? 'blob' : 'code/data'
}))

export function allowReadSnapshot(method, path) {
  const endpoint = adminEndpoints.find((e) => e.path === path && e.method === method && e.effect === 'read')
  if (!endpoint) throw new Error(`Blocked baseline request: ${method} ${path}`)
  return endpoint
}

export const protocolCases = {
  manage: {
    input: { documentId: '90071992547409931234', pageNo: 1, filters: [0.45, { enabled: false }], nullable: null },
    wire: { documentId: '90071992547409931234', pageNo: '1', filters: ['0.45', { enabled: false }], nullable: null }
  },
  config: { configKey: 'ragRuntime.minVectorSimilarity', value: 0.45, expectedVersion: 6, changeNote: '隔离测试' },
  logId: '90071992547409931235'
}
