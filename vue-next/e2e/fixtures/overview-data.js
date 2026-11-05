export const documentPage = {
  total: '12', pageNo: '1', pageSize: '200', records: [
    { documentId: '90071992547409931234', documentName: '团队知识使用指南', knowledgeBaseName: '团队资料库', originalFileName: 'team-guide.pdf', parseStatus: '3', strategyStatus: '3', indexStatus: '3', indexStatusName: '索引完成', editTime: '2026-11-04T09:00:00+08:00' },
    { documentId: '90071992547409931235', documentName: '项目接入说明', knowledgeBaseName: '项目资料库', originalFileName: 'getting-started.md', parseStatus: '3', strategyStatus: '1', indexStatus: '1', indexStatusName: '待构建', editTime: '2026-11-03T16:30:00+08:00' },
    { documentId: '90071992547409931236', documentName: '常见问题', knowledgeBaseName: '团队资料库', originalFileName: 'faq.pdf', parseStatus: '4', strategyStatus: '1', indexStatus: '1', indexStatusName: '待构建', editTime: '2026-11-03T14:20:00+08:00' }
  ]
}
export const routePage = { total: '372', records: [
  { id: '90071992547409931237', mode: 'auto', routeStatus: '1', confidence: '0.82', hitSelectedDocument: '' },
  { id: '90071992547409931238', mode: 'shadow', routeStatus: '2', confidence: '0.51', hitSelectedDocument: '1' },
  { id: '90071992547409931239', mode: 'auto', routeStatus: '3', confidence: '', hitSelectedDocument: '' }
] }
export function qualityData(windowDays = '0') {
  const labels = { '0': '全部时间', '7': '近 7 天', '30': '近 30 天', '90': '近 90 天' }
  return {
    windowDays, windowLabel: labels[windowDays], comparable: windowDays === '0' ? '0' : '1', truncated: '0',
    metrics: [
      { key: 'route-total', label: '路由总量', value: '372', valueType: 'count', previousValue: '68', delta: '304', higherIsBetter: '1', hint: '自动与影子路由合计', sparkline: [{ date: '11-01', value: '100' }, { date: '11-02', value: '109' }, { date: '11-03', value: '6' }, { date: '11-04', value: '157' }] },
      { key: 'success-rate', label: '路由成功率', value: '59.1', valueType: 'rate', previousValue: '63.4', delta: '-4.3', higherIsBetter: '1', hint: '成功次数 / 路由总量', sparkline: [] },
      { key: 'confidence', label: '平均置信度', value: '0.567', valueType: 'score', previousValue: '0.550', delta: '0.017', higherIsBetter: '1', hint: '有记录的路由置信度均值', sparkline: [] },
      { key: 'failed', label: '失败次数', value: '0', valueType: 'count', previousValue: '2', delta: '-2', higherIsBetter: '0', hint: '失败状态的路由数量', sparkline: [] }
    ],
    routeTrend: [
      { date: '2026-11-01', dateLabel: '11-01', autoCount: '43', autoSuccessRate: '48.8', autoConfidence: '0.544', shadowCount: '57', shadowSuccessRate: '63.2', shadowConfidence: '0.587' },
      { date: '2026-11-02', dateLabel: '11-02', autoCount: '48', autoSuccessRate: '50.0', autoConfidence: '0.541', shadowCount: '61', shadowSuccessRate: '63.9', shadowConfidence: '0.585' },
      { date: '2026-11-03', dateLabel: '11-03', autoCount: '', autoSuccessRate: '', autoConfidence: '', shadowCount: '6', shadowSuccessRate: '66.7', shadowConfidence: '0.575' },
      { date: '2026-11-04', dateLabel: '11-04', autoCount: '34', autoSuccessRate: '64.7', autoConfidence: '0.612', shadowCount: '123', shadowSuccessRate: '60.2', shadowConfidence: '0.569' }
    ],
    channelProfiles: [
      { channelType: 'vector', channelName: '向量检索', recalledCount: '70', selectedCount: '22', selectionRate: '31.4', resolutionRate: '64.3', snapshotCount: '70', executionCount: '8' },
      { channelType: 'keyword', channelName: '关键词检索', recalledCount: '70', selectedCount: '19', selectionRate: '27.1', resolutionRate: '32.9', snapshotCount: '70', executionCount: '8' },
      { channelType: 'table', channelName: '表格检索', recalledCount: '1', selectedCount: '0', selectionRate: '0.0', resolutionRate: '0.0', snapshotCount: '1', executionCount: '8' }
    ],
    routeDecision: { total: '372', success: '220', lowConfidence: '152', failed: '0', successRate: '59.1' },
    docProcessing: { parseCount: '31', indexCount: '31', averageParseMs: '5000', averageIndexMs: '150000', indexToParseRatio: '30', items: [
      { date: '2026-11-01', dateLabel: '11-01', parseCount: '4', averageParseMs: '5000', indexCount: '4', averageIndexMs: '150000' },
      { date: '2026-11-04', dateLabel: '11-04', parseCount: '27', averageParseMs: '5000', indexCount: '27', averageIndexMs: '150000' }
    ] }
  }
}
