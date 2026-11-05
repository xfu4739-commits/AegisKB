import { http } from '../../../shared/api/http.js'
import { WINDOW_OPTIONS } from './overviewModel.js'

export function createOverviewApi(client = http) {
  return {
    documents: (signal) => client.json('/manage/document/page/query', { pageNo: '1', pageSize: '200', keyword: '' }, { signal }),
    routes: (signal) => client.json('/manage/knowledge/route/trace/page/query', { pageNo: '1', pageSize: '200' }, { signal }),
    quality(windowDays, signal) {
      if (!WINDOW_OPTIONS.some((o) => o.value === windowDays)) throw new Error('请选择有效时间窗。')
      return client.json('/manage/observability/quality/overview/query', { windowDays }, { signal })
    }
  }
}
export const overviewApi = createOverviewApi()
