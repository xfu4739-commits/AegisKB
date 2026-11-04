// One deterministic backend snapshot for both independent applications.
// All business requests are intercepted, including popups opened by links.
export const acceptanceAnswer = `## 一个清晰的工作流

先明确问题和回答范围，再查看结果与来源。这里使用相同的隔离数据比较两版界面，不调用模型。

| 步骤 | 目的 | 结果 |
| --- | --- | --- |
| 选择范围 | 明确知识边界 | 保留字符串 ID |
| 阅读回答 | 查看结构化内容 | 检查表格、代码和引用 |

\`\`\`js
const scope = { documentId: '90071992547409931234' }
console.log(scope)
\`\`\`

已绑定的来源保持原编号 [1]，未知编号 [99] 不应变成来源。

${'这是一段用于检查长回答阅读宽度、换行和滚动的固定文字。\n\n'.repeat(10)}`

export const acceptanceSource = { citationIdentity: 'acceptance-source-1', referenceId: 'acceptance-source-1', citationEvidenceType: 'PROMPT_RENDERED_SOURCE', documentName: '前端验收示例文档', knowledgeBaseName: '隔离资料库', documentId: '90071992547409931234', quoteText: '这是后端绑定的原始片段。' + '定位和片段内容保持完整，不在前端推断或补充。'.repeat(30), sectionPath: '示例 / 工作流程', pageNo: '12', chunkId: 'acceptance-chunk-1' }

export function acceptanceSession(scene = 'history') {
  return { conversationId: 'acceptance-session', latestUserMessage: '如何把一个想法整理成可执行的计划？', updatedAt: '2026-11-03 12:00:00', chatMode: 'OPEN_CHAT', knowledgeBaseSelectionMode: 'NONE', selectedKnowledgeBaseIds: [], selectedDocumentId: null,
    exchanges: [{ exchangeId: '90071992547409931235', question: '如何把一个想法整理成可执行的计划？', answer: acceptanceAnswer, references: [acceptanceSource], recommendations: ['为这个计划列出检查项'], createdAt: '2026-11-03T12:00:00+08:00', status: scene === 'error' ? 'FAILED' : 'COMPLETED', errorMessage: scene === 'error' ? '隔离验收：回答服务中断，已有内容仍然保留。' : '' }] }
}

export async function mockAcceptance(context, scene = 'history') {
  const calls = []
  await context.route((url) => /^\/(?:api|manage|admin\/auth)\//.test(url.pathname), async (route) => {
    const path = new URL(route.request().url()).pathname
    calls.push(path)
    let data
    if (path === '/api/chat/session/list') data = { sessions: scene === 'welcome' ? [] : [acceptanceSession(scene)], totalPages: '1', pageNo: '1', pageSize: '200', totalSize: scene === 'welcome' ? '0' : '1' }
    else if (path === '/api/chat/session/detail') data = acceptanceSession(scene)
    else if (path === '/api/chat/knowledge-base/options') data = [{ id: 'kb-acceptance', baseName: '隔离资料库' }]
    else if (path === '/api/chat/document/options') data = [{ documentId: '90071992547409931234', knowledgeBaseId: 'kb-acceptance', documentName: '前端验收示例文档' }]
    else if (path === '/api/chat/exchange/retrieval/results') data = []
    else if (path === '/manage/knowledge/route/trace/page/query') data = { records: [], total: '0' }
    else throw new Error(`Unmocked business request: ${path}`)
    await route.fulfill({ json: { code: '0', data } })
  })
  return calls
}
