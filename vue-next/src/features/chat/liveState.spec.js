import { describe, expect, it, vi } from 'vitest'
import { useLiveState } from './liveState.js'

const detail = (id) => ({ conversationId: id, chatMode: 'DOCUMENT', knowledgeBaseSelectionMode: 'SELECTED', selectedKnowledgeBaseIds: ['90071992547409931234'], selectedDocumentId: 'doc', exchanges: [{ exchangeId: '10000000000000000001', question: '问题', answer: '答案' }] })
const api = () => ({ list: vi.fn().mockResolvedValue([{ conversationId: 'a', latestUserMessage: '真实标题' }, { conversationId: 'b' }]), knowledgeBases: vi.fn().mockResolvedValue([{ id: '90071992547409931234', baseName: '资料库' }]), documents: vi.fn().mockResolvedValue([{ documentId: 'doc', knowledgeBaseId: '90071992547409931234', documentName: '文档' }]), detail: vi.fn(async (id) => detail(id)), remove: vi.fn().mockResolvedValue(null) })
describe('live session ownership', () => {
  it('restores string IDs, exchanges and exact saved scope without writes', async () => {
    const client = api(); const state = useLiveState(client); await state.load()
    expect(state.messages.value.map((item) => item.content)).toEqual(['问题', '答案'])
    expect(state.scope.value).toEqual({ mode: 'DOCUMENT', knowledgeBaseIds: ['90071992547409931234'], documentId: 'doc' })
    expect(client.remove).not.toHaveBeenCalled()
    expect(state.send('')).toBe(false)
  })
  it('prevents late details from replacing a newer selection or a new conversation', async () => {
    const client = api(); const state = useLiveState(client); await state.load()
    let release; client.detail.mockImplementationOnce(() => new Promise((resolve) => { release = resolve }))
    const pending = state.select('b'); state.reset(); release(detail('b')); await pending
    expect(state.currentId.value).toBe(''); expect(state.messages.value).toEqual([])
  })
  it('keeps the last valid conversation on detail failure and retains sessions on failed deletion', async () => {
    const client = api(); const state = useLiveState(client); await state.load()
    client.detail.mockRejectedValueOnce(new Error('详情失败')); await state.select('b')
    expect(state.currentId.value).toBe('a')
    client.remove.mockRejectedValueOnce(new Error('删除失败')); await state.remove('a')
    expect(state.sessions.value).toHaveLength(2); expect(state.error.value).toContain('删除失败')
    client.remove.mockResolvedValueOnce(null); await state.remove('a')
    expect(state.sessions.value).toHaveLength(1); expect(state.messages.value).toEqual([])
  })
  it('does not silently change historical scope when options fail', async () => {
    const client = api(); client.documents.mockRejectedValue(new Error('文档选项失败'))
    const state = useLiveState(client); await state.load()
    expect(state.scope.value.documentId).toBe('doc'); expect(state.scopeReady.value).toBe(false)
    expect(state.error.value).toContain('文档选项失败')
  })
})
