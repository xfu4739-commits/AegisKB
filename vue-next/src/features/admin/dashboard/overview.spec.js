import { expect, it, vi } from 'vitest'
import { summarizeDocuments, summarizeRoutes, normalizeQuality, optionalNumber, lineGeometry } from './overviewModel.js'
import { createReadResource } from './overviewState.js'
import { documentPage, routePage, qualityData } from '../../../../e2e/fixtures/overview-data.js'
import { createOverviewApi } from './overviewApi.js'

it('document counts remain sample counts and entity IDs remain strings', () => {
  const result = summarizeDocuments(documentPage)
  expect(result).toMatchObject({ total: 12, sampleSize: 3, partial: true, parseSuccess: 2, strategyConfirmed: 1, indexSuccess: 1 })
  expect(result.records[0].documentId).toBe('90071992547409931234')
  expect(summarizeDocuments({ records: [] }).total).toBeNull()
  expect(() => summarizeDocuments(null)).toThrow()
})
it('route statistics use the returned sample, ignoring missing confidence and unknown statuses', () => {
  expect(summarizeRoutes(routePage)).toMatchObject({ sampleSize: 3, total: 372, success: 1, lowConfidence: 1, failed: 1, averageConfidence: 0.665, shadowHitRate: 100 })
  const result = summarizeRoutes({ records: [{ mode: 'auto', routeStatus: 'unknown', confidence: null }] })
  expect(result.unknown).toBe(1); expect(result.failed).toBe(0); expect(result.successRate).toBeNull()
})
it('empty, boolean and non-finite fields never become synthetic zero; genuine zero remains', () => {
  for (const value of [null, undefined, '', ' ', false, true, 'NaN', Infinity]) expect(optionalNumber(value)).toBeNull()
  expect(optionalNumber('0')).toBe(0)
  const input = qualityData('7'); input.channelProfiles[0].selectedCount = ''; input.docProcessing.averageParseMs = null
  const view = normalizeQuality(input, '7')
  expect(view.channels[0].selectedCount).toBeNull(); expect(view.channels[2].selectedCount).toBe(0)
  expect(view.processing.averageParseMs).toBeNull()
})
it('time-window authority and backend delta direction are preserved', () => {
  expect(normalizeQuality(qualityData('0'), '0').metrics[0].deltaText).toBe('')
  const data = normalizeQuality(qualityData('7'), '7')
  expect(data.metrics[1]).toMatchObject({ valueText: '59.1%', deltaText: '-4.3 个百分点', deltaTone: 'danger' })
  expect(data.metrics[3]).toMatchObject({ valueText: '0', deltaTone: 'positive' })
  expect(() => normalizeQuality(qualityData('30'), '7')).toThrow(/时间窗/)
})
it('trend geometry breaks at missing points, keeps real zeros, and never extrapolates', () => {
  const geometry = lineGeometry([0, null, 80, 100], 100, 400, 180)
  expect(geometry.segments.map((s) => s.length)).toEqual([1, 2])
  expect(geometry.segments[0][0].value).toBe(0)
  expect(lineGeometry([null, null], 1, 400, 180).segments).toEqual([])
})
it('overview requests are only the three existing POST reads, with numeric wire strings', async () => {
  const client = { json: vi.fn().mockResolvedValue({}) }, api = createOverviewApi(client), signal = new AbortController().signal
  await api.documents(signal); await api.routes(signal); await api.quality('30', signal)
  expect(client.json.mock.calls.map(([path, body]) => [path, body])).toEqual([
    ['/manage/document/page/query', { pageNo: '1', pageSize: '200', keyword: '' }],
    ['/manage/knowledge/route/trace/page/query', { pageNo: '1', pageSize: '200' }],
    ['/manage/observability/quality/overview/query', { windowDays: '30' }]
  ])
  expect(() => api.quality('all')).toThrow()
})
it('read resources keep prior data only for the same scope, and reject late results', async () => {
  const waiting = [], loader = vi.fn((key, signal) => new Promise((resolve, reject) => waiting.push({ key, signal, resolve, reject })))
  const state = createReadResource(loader)
  const first = state.load('7'); waiting[0].resolve('seven'); await first
  const refresh = state.load('7'); waiting[1].reject(new Error('refresh failed')); await refresh
  expect(state.data.value).toBe('seven'); expect(state.error.value).toBe('refresh failed')
  const old = state.load('30'); expect(state.data.value).toBeNull()
  const latest = state.load('90'); expect(waiting[2].signal.aborted).toBe(true)
  waiting[3].resolve('ninety'); await latest; waiting[2].resolve('thirty'); await old
  expect(state.data.value).toBe('ninety')
  const leaving = state.load('0'); state.dispose(); waiting[4].resolve('all'); await leaving
  expect(state.data.value).toBeNull()
})
