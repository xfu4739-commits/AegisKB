import { mockAdminAuth } from './admin-auth.js'
import { documentPage, routePage, qualityData } from './overview-data.js'

export async function mockOverview(context, scene = 'full') {
  const auth = await mockAdminAuth(context)
  const aborted = new Set()
  context.on('requestfailed', (request) => { if (request.failure()?.errorText?.includes('ERR_ABORTED')) aborted.add(request) })
  const state = { auth, calls: [], completed: [], documentStatus: 200, routeStatus: 200, qualityStatus: 200, delays: {} }
  const paths = ['/manage/document/page/query', '/manage/knowledge/route/trace/page/query', '/manage/observability/quality/overview/query']
  await context.route((url) => paths.includes(url.pathname), async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname, body = request.postDataJSON()
    if (request.method() !== 'POST') { auth.blocked.push(`${request.method()} ${path}`); await route.abort(); return }
    state.calls.push({ path, body })
    const type = path === paths[0] ? 'document' : path === paths[1] ? 'route' : 'quality'
    const window = body.windowDays
    if (type === 'quality' && state.delays[window]) await new Promise((resolve) => setTimeout(resolve, state.delays[window]))
    let data = structuredClone(type === 'document' ? documentPage : type === 'route' ? routePage : qualityData(window))
    if (scene === 'empty') data = type === 'quality' ? { windowDays: window, windowLabel: '全部时间', comparable: '0', metrics: [], routeTrend: [], channelProfiles: [], routeDecision: { total: '0' }, docProcessing: null } : { records: [], total: '0' }
    if (scene === 'missing' && type === 'quality') { data.metrics[1].value = ''; data.channelProfiles[0].selectedCount = null; data.docProcessing.averageParseMs = ''; data.truncated = '1' }
    const status = state[`${type}Status`]
    try {
      await route.fulfill({ status, json: status === 200 ? { code: '0', data } : { code: String(status), message: status === 403 ? '无权读取统计' : '隔离场景：统计服务暂不可用' } })
    } catch (error) { if (!aborted.has(request)) throw error }
    finally { state.completed.push({ type, window }) }
  })
  return state
}
