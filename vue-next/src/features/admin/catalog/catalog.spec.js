import { it, expect, vi } from 'vitest'
import { createBaseDraft, buildBasePayload, readListQuery, pageSlice, documentBusy, validateUpload, uploadForm, normalizeDocumentPage } from './catalogModel.js'
import { createCatalogApi } from './catalogApi.js'
import { createMutation } from './catalogState.js'
import { bases, baseId } from '../../../../e2e/fixtures/catalog-data.js'

it('base editing preserves unknown nested fields and metadata without inventing identity', () => {
  const draft = createBaseDraft(bases[0]); draft.baseName = '  新名称  '
  const result = buildBasePayload(draft)
  expect(result.id).toBe(baseId); expect(result.baseName).toBe('新名称'); expect(result.operatorId).toBeUndefined()
  expect(JSON.parse(result.retrievalConfigJson)).toEqual(JSON.parse(bases[0].retrievalConfigJson))
  expect(JSON.parse(result.graphRagConfigJson).build.extra).toBe('keep')
  expect(result.metadataFilterJson).toBe(bases[0].metadataFilterJson)
})
it('invalid stored JSON never silently becomes an empty config', () => {
  expect(() => createBaseDraft({ ...bases[0], retrievalConfigJson: '{bad' })).toThrow(/JSON/)
  expect(() => createBaseDraft({ ...bases[0], retrievalConfigJson: '[]' })).toThrow(/对象/)
})
it('new bases have editable recommended values, editing sparse bases preserves omissions', () => {
  expect(JSON.parse(buildBasePayload({ ...createBaseDraft(), baseName: '新库' }).retrievalConfigJson).vectorTopK).toBe(10)
  expect(JSON.parse(buildBasePayload(createBaseDraft(bases[1])).retrievalConfigJson)).toEqual({})
})
it('numeric config validates integer/minimum and retains boolean flags', () => {
  const draft = createBaseDraft(bases[0]); draft.configs.retrieval.vectorTopK = 1.5
  expect(() => buildBasePayload(draft)).toThrow(/整数/)
  draft.configs.retrieval.vectorTopK = '20'
  expect(JSON.parse(buildBasePayload(draft).retrievalConfigJson)).toMatchObject({ vectorTopK: 20, keywordChannelEnabled: true })
  draft.metadataFilterJson = '[]'; expect(() => buildBasePayload(draft)).toThrow()
})
it('URL state is canonical and local pagination uses the filtered list', () => {
  expect(readListQuery({ q: '  资料 ', page: '-2', size: '999' })).toEqual({ keyword: '资料', page: 1, size: 12 })
  expect(pageSlice(bases, '产品', 1, 10).records[0].id).toBe(bases[1].id)
})
it('missing list totals are invalid rather than zero and long IDs remain strings', () => {
  expect(() => normalizeDocumentPage({ records: [] })).toThrow()
  expect(normalizeDocumentPage({ records: [{ documentId: baseId }], total: '1', pageNo: '1', pageSize: '12' }).records[0].documentId).toBe(baseId)
})
it('active tasks prohibit deletion using returned status codes', () => {
  expect(documentBusy({ parseStatus: '2' })).toBe(true)
  expect(documentBusy({ latestTaskStatus: '1' })).toBe(true)
  expect(documentBusy({ parseStatus: '3', indexStatus: '3', latestTaskStatus: '3' })).toBe(false)
})
it('upload requires explicit valid scope, operator and file; never defaults either identity', () => {
  const file = new File(['content'], '指南.pdf'), draft = { file, knowledgeBaseId: baseId, documentName: ' 指南 ', operatorId: '42' }
  expect(validateUpload(draft, bases)).toEqual([])
  expect(validateUpload({ ...draft, operatorId: '' }, bases)).not.toEqual([])
  expect(validateUpload({ ...draft, knowledgeBaseId: 'unknown' }, bases)).not.toEqual([])
  expect(validateUpload({ ...draft, file: new File(['bad'], 'bad.exe') }, bases)).not.toEqual([])
  const form = uploadForm(draft, bases)
  expect([...form.keys()]).toEqual(['file', 'meta'])
  expect(form.get('file').name).toBe('指南.pdf'); expect(form.get('meta').type).toBe('application/json')
})
it('catalog API preserves existing endpoints and wire string types', async () => {
  const http = { json: vi.fn().mockResolvedValue(true), multipart: vi.fn().mockResolvedValue({}) }, api = createCatalogApi(http)
  await api.bases(); await api.documents({ keyword: '计划', page: 2, size: 12 }); await api.deleteBase(baseId); await api.deleteDocument(baseId)
  expect(http.json.mock.calls.map(([p,b]) => [p,b])).toEqual([
    ['/manage/knowledge/base/list', {}], ['/manage/document/page/query', { keyword: '计划', pageNo: '2', pageSize: '12' }], ['/manage/knowledge/base/delete', { id: baseId }], ['/manage/document/delete', { documentId: baseId }]
  ])
})
it('mutations lock before sending and ignore results after page disposal', async () => {
  let resolve; const send = vi.fn(() => new Promise(r => { resolve = r })), success = vi.fn(), state = createMutation()
  const first = state.run(send, success); await state.run(send, success)
  expect(send).toHaveBeenCalledTimes(1)
  state.dispose(); resolve(true); await first; expect(success).not.toHaveBeenCalled()
})
