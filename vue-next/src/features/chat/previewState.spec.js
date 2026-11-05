import { describe, expect, it, vi } from 'vitest'
import { usePreviewState, reconcileScope } from './previewState.js'

describe('preview scope', () => {
  const docs = [{ id: 'doc-a', knowledgeBaseId: 'kb-a' }, { id: 'doc-b', knowledgeBaseId: 'kb-b' }]
  it('clears a document outside the selected knowledge bases and fails closed when none remain', () => {
    const scope = reconcileScope({ mode: 'DOCUMENT', knowledgeBaseIds: ['kb-b'], documentId: 'doc-a' }, docs)
    expect(scope.documentId).toBe('')
    expect(scope.mode).toBe('DOCUMENT')
    expect(reconcileScope({ ...scope, knowledgeBaseIds: [] }, docs)).toEqual({ mode: 'OPEN_CHAT', knowledgeBaseIds: [], documentId: '' })
  })
})

describe('isolated demo state', () => {
  it('rejects an HTML fallback from a stale development server', async () => {
    const json = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, headers: new Headers({ 'content-type': 'text/html' }), json })
    const state = usePreviewState(true)
    await state.load('history')
    expect(state.status.value).toBe('error')
    expect(json).not.toHaveBeenCalled()
    expect(state.error.value).toBe('演示资料暂时无法加载，请重试。')
  })

  it('does not request data when the demo is disabled', async () => {
    const request = vi.fn()
    const state = usePreviewState(false, request)
    await state.load('history')
    state.send('hello')
    expect(request).not.toHaveBeenCalled()
    expect(state.messages.value).toEqual([])
  })

  it('ignores stale responses after switching scenario or creating a new conversation', async () => {
    let resolveOld
    const request = vi.fn().mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
    const state = usePreviewState(true, request)
    const loading = state.load('history')
    state.reset()
    resolveOld({ sessions: [{ id: 'old', title: '旧结果', messages: [] }], knowledgeBases: [], documents: [] })
    await loading
    expect(state.sessions.value).toEqual([])
    expect(state.currentId.value).toBe('')
  })

  it('shows errors and supports a fresh retry, without manufacturing successful data', async () => {
    const request = vi.fn().mockRejectedValueOnce(new Error('演示数据加载失败')).mockResolvedValueOnce({ sessions: [], knowledgeBases: [], documents: [] })
    const state = usePreviewState(true, request)
    await state.load('error')
    expect(state.status.value).toBe('error')
    expect(state.sessions.value).toEqual([])
    await state.load('welcome')
    expect(state.status.value).toBe('ready')
    expect(state.error.value).toBe('')
  })
})
