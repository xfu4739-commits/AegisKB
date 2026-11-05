export const WINDOW_OPTIONS = [
  { value: '7', label: '近 7 天' }, { value: '30', label: '近 30 天' },
  { value: '90', label: '近 90 天' }, { value: '0', label: '全部时间' }
]
export const optionalNumber = (value) => {
  if (value == null || typeof value === 'boolean' || String(value).trim() === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}
const flag = (value) => ['1', 'true'].includes(String(value).toLowerCase())
const list = (value) => {
  if (value == null) return []
  if (!Array.isArray(value)) throw new Error('统计列表格式不正确。')
  return value
}
export function valueText(value, type = 'count') {
  const number = optionalNumber(value)
  if (number === null) return '—'
  if (type === 'rate') return `${number.toFixed(1)}%`
  if (type === 'score') return number.toFixed(3)
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(number)
}
export function durationText(value) {
  const ms = optionalNumber(value)
  if (ms === null || ms < 0) return '—'
  if (ms < 1000) return `${Math.round(ms)} ms`
  if (ms < 60000) return `${Number((ms / 1000).toFixed(2))} s`
  const seconds = Math.round(ms / 1000)
  return `${Math.floor(seconds / 60)} m${seconds % 60 ? ` ${seconds % 60} s` : ''}`
}
export function summarizeDocuments(payload) {
  if (!payload || !Array.isArray(payload.records) || payload.records.some((row) => !row || typeof row !== 'object')) throw new Error('文档统计格式不正确。')
  const records = payload.records, total = optionalNumber(payload.total)
  return { records, total, sampleSize: records.length, partial: total !== null && total > records.length,
    parseSuccess: records.filter((r) => String(r.parseStatus) === '3').length,
    strategyConfirmed: records.filter((r) => String(r.strategyStatus) === '3').length,
    indexSuccess: records.filter((r) => String(r.indexStatus) === '3').length }
}
export function summarizeRoutes(payload) {
  if (!payload || !Array.isArray(payload.records)) throw new Error('路由统计格式不正确。')
  const records = payload.records, states = records.map((r) => ({ '1': 'success', SUCCESS: 'success', '2': 'lowConfidence', LOW_CONFIDENCE: 'lowConfidence', '3': 'failed', FAILED: 'failed' })[r?.routeStatus] || 'unknown')
  const counts = Object.fromEntries(['success', 'lowConfidence', 'failed', 'unknown'].map((s) => [s, states.filter((state) => state === s).length]))
  const confidence = records.map((r) => optionalNumber(r.confidence)).filter((v) => v !== null)
  const shadow = records.filter((r) => r.mode === 'shadow' && ['0', '1'].includes(String(r.hitSelectedDocument)))
  const denominator = records.length && !counts.unknown ? records.length : null
  return { ...counts, total: optionalNumber(payload.total), sampleSize: records.length,
    successRate: denominator ? counts.success / denominator * 100 : null,
    lowFailureRate: denominator ? (counts.lowConfidence + counts.failed) / denominator * 100 : null,
    averageConfidence: confidence.length ? confidence.reduce((a, b) => a + b, 0) / confidence.length : null,
    highConfidence: confidence.filter((v) => v >= 0.8).length,
    autoCount: records.filter((r) => r.mode === 'auto').length, shadowCount: records.filter((r) => r.mode === 'shadow').length,
    shadowHitRate: shadow.length ? shadow.filter((r) => String(r.hitSelectedDocument) === '1').length / shadow.length * 100 : null }
}
const numericFields = (raw, fields) => Object.fromEntries(fields.map((field) => [field, optionalNumber(raw?.[field])]))
export function normalizeQuality(payload, requested) {
  if (!payload || String(payload.windowDays) !== requested || !WINDOW_OPTIONS.some((o) => o.value === requested)) throw new Error('统计返回的时间窗与请求不一致。')
  const comparable = flag(payload.comparable)
  const metrics = list(payload.metrics).map((item, index) => {
    const delta = comparable ? optionalNumber(item.delta) : null
    const directionKnown = ['1', '0', 'true', 'false'].includes(String(item.higherIsBetter).toLowerCase())
    const improved = flag(item.higherIsBetter) ? delta > 0 : delta < 0
    const deltaBody = delta === null ? '' : item.valueType === 'rate' ? `${Math.abs(delta).toFixed(1)} 个百分点` : item.valueType === 'score' ? Math.abs(delta).toFixed(3) : String(Math.round(Math.abs(delta)))
    return { key: String(item.key || index), label: String(item.label || '未命名指标'), value: optionalNumber(item.value), valueText: valueText(item.value, item.valueType), valueType: item.valueType,
      previousText: valueText(item.previousValue, item.valueType), hint: String(item.hint || ''),
      deltaText: delta === null ? '' : delta === 0 ? '持平' : `${delta > 0 ? '+' : '-'}${deltaBody}`,
      deltaTone: delta === null || delta === 0 || !directionKnown ? 'neutral' : improved ? 'positive' : 'danger',
      history: list(item.sparkline).map((p) => ({ date: String(p.date || ''), value: optionalNumber(p.value) })) }
  })
  const trend = list(payload.routeTrend).map((p) => ({ date: p.date, label: p.dateLabel || p.date,
    ...numericFields(p, ['autoCount', 'autoSuccessRate', 'autoConfidence', 'shadowCount', 'shadowSuccessRate', 'shadowConfidence']) }))
  const channels = list(payload.channelProfiles).map((p, index) => ({ key: String(p.channelType || index), name: String(p.channelName || p.channelType || '未命名通道'),
    ...numericFields(p, ['recalledCount', 'selectedCount', 'selectionRate', 'resolutionRate', 'snapshotCount', 'executionCount']) }))
  const decision = payload.routeDecision ? numericFields(payload.routeDecision, ['total', 'success', 'lowConfidence', 'failed', 'successRate']) : null
  const normalizeProcessing = (p) => {
    const values = numericFields(p, ['parseCount', 'indexCount', 'averageParseMs', 'averageIndexMs', 'indexToParseRatio'])
    if (values.parseCount === 0) values.averageParseMs = null
    if (values.indexCount === 0) values.averageIndexMs = null
    return values
  }
  const processing = payload.docProcessing ? { ...normalizeProcessing(payload.docProcessing), items: list(payload.docProcessing.items).map((p) => ({ date: p.date, label: p.dateLabel || p.date, ...normalizeProcessing(p) })) } : null
  const hasData = metrics.some((m) => m.value !== null && m.value !== 0) || trend.length > 0 || channels.length > 0 || decision?.total > 0 || processing?.parseCount > 0 || processing?.indexCount > 0
  return { windowDays: requested, windowLabel: String(payload.windowLabel || WINDOW_OPTIONS.find((o) => o.value === requested).label), comparable, truncated: flag(payload.truncated), metrics, trend, channels, decision, processing, hasData }
}

export function lineGeometry(values, nominalMax, width, height, minValue = 0, maxValue) {
  const valid = values.filter((v) => v !== null && Number.isFinite(v))
  const min = Math.min(minValue, ...valid), max = maxValue ?? Math.max(nominalMax, ...valid)
  const x = (i) => values.length > 1 ? i * width / (values.length - 1) : width / 2
  const y = (v) => height - (v - min) / (max - min || 1) * height
  const segments = []; let current = []
  values.forEach((value, index) => {
    if (value !== null && Number.isFinite(value)) current.push({ x: x(index), y: y(value), value, index })
    else if (current.length) { segments.push(current); current = [] }
  })
  if (current.length) segments.push(current)
  return { segments, min, max }
}
