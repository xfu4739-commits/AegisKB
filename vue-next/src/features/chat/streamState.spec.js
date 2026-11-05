import { describe, it, expect, vi } from 'vitest'
import { useLiveState } from './liveState.js'

function fixture() {
  let event, finish
  const controller = new AbortController()
  const api = { list: async () => [], knowledgeBases: async () => [], documents: async () => [],
    stream: vi.fn((payload, callback) => { event = callback; return { controller, done: new Promise((resolve) => { finish = resolve }) } }),
    stop: vi.fn(async () => {}), detail: vi.fn(async () => { throw new Error('归档暂不可用') }), remove: vi.fn() }
  const state = useLiveState(api)
  return { state, api, controller, emit: (value) => event(value), finish: () => finish() }
}
describe('live turn ownership', () => {
  it('preserves the draft answer if archive refresh only returns a previous exchange', async () => {
    const f = fixture()
    f.api.list = async () => [{ conversationId: 'existing' }]
    f.api.detail.mockResolvedValue({ conversationId: 'existing', exchanges: [{ exchangeId: 'old', question: '旧问题', answer: '旧答案' }] })
    await f.state.load(); f.state.send('新问题'); f.emit({ type: 'text', content: '新答案' }); f.finish()
    await vi.waitFor(() => expect(f.state.busy.value).toBe(false))
    expect(f.state.messages.value.at(-1).content).toBe('新答案')
    expect(f.state.error.value).toContain('本轮归档尚未就绪')
  })
  it('sends string document scope exactly, and scope changes cannot mutate an active payload', async () => {
    const f = fixture()
    f.api.knowledgeBases = async () => [{ id: '90071992547409931234', baseName: '资料库' }]
    f.api.documents = async () => [{ documentId: 'doc', knowledgeBaseId: '90071992547409931234', documentName: '文档' }]
    await f.state.load(); f.state.updateScope({ knowledgeBaseIds: ['90071992547409931234'], mode: 'DOCUMENT', documentId: 'doc' })
    f.state.send('问题'); f.state.updateScope({ knowledgeBaseIds: [] })
    expect(f.api.stream.mock.calls[0][0]).toMatchObject({ chatMode: 'DOCUMENT', selectedDocumentId: 'doc', knowledgeBaseSelectionMode: 'SELECTED', selectedKnowledgeBaseIds: ['90071992547409931234'] })
    expect(f.state.scope.value.documentId).toBe('doc')
    f.state.cancel()
  })
  it('freezes scope, rejects duplicate/conflicting actions, and ignores late fragments after stop', async () => {
    const f = fixture(); await f.state.load()
    expect(f.state.send(' 问题 ')).toBe(true)
    expect(f.api.stream.mock.calls[0][0]).toMatchObject({ question: '问题', chatMode: 'OPEN_CHAT', knowledgeBaseSelectionMode: 'NONE', selectedKnowledgeBaseIds: [], selectedDocumentId: null })
    const id = f.state.currentId.value
    expect(f.state.send('重复')).toBe(false)
    f.state.reset(); await f.state.select('other'); await f.state.remove(id)
    expect(f.state.currentId.value).toBe(id); expect(f.api.remove).not.toHaveBeenCalled()
    f.emit({ type: 'text', content: '保留内容' })
    await f.state.stop()
    f.emit({ type: 'text', content: '迟到' }); f.finish(); await Promise.resolve()
    expect(f.controller.signal.aborted).toBe(true)
    expect(f.api.stop).toHaveBeenCalledWith(id)
    expect(f.state.messages.value.at(-1).content).toBe('保留内容')
    expect(f.state.messages.value.at(-1).statusText).toContain('停止')
    expect(f.state.busy.value).toBe(false)
  })
  it('preserves partial answers on failure and retries explicitly with frozen turn scope', async () => {
    const f = fixture(); await f.state.load(); f.state.send('问题')
    f.emit({ type: 'text', content: '部分答案' }); f.emit({ type: 'error', content: '服务失败' }); f.finish()
    await vi.waitFor(() => expect(f.state.busy.value).toBe(false))
    expect(f.state.messages.value.at(-1)).toMatchObject({ content: '部分答案', error: '服务失败' })
    expect(f.state.retryMessage(f.state.messages.value.at(-1).id)).toBe(true)
    expect(f.api.stream).toHaveBeenCalledTimes(2)
    f.state.cancel(); f.emit({ type: 'text', content: '卸载后' })
    expect(f.state.messages.value.at(-1).content).toBe('')
  })
  it('keeps conflicting actions locked until server stop is acknowledged, allowing stop retry', async () => {
    const f = fixture(); await f.state.load(); f.state.send('问题')
    f.api.stop.mockRejectedValueOnce(new Error('停止失败'))
    await f.state.stop(); expect(f.state.busy.value).toBe(true)
    expect(f.state.error.value).toContain('停止失败')
    await f.state.stop(); expect(f.state.busy.value).toBe(false)
  })
})
