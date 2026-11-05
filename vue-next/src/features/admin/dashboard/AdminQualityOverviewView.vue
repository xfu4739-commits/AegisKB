<script setup>
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Empty, Modal, RadioButton, RadioGroup, Skeleton } from 'ant-design-vue'
import { ArrowPathIcon } from '@heroicons/vue/24/outline'
import { overviewApi } from './overviewApi.js'
import { createReadResource } from './overviewState.js'
import { durationText, normalizeQuality, valueText, WINDOW_OPTIONS } from './overviewModel.js'
import { legacyAdminLink } from './overviewLinks.js'
import MetricStrip from './MetricStrip.vue'
import OverviewChart from './OverviewChart.vue'
import './overview.css'

const route = useRoute(), router = useRouter(), popupContainer = inject('admin-next-container')
const resource = createReadResource((window, signal) => overviewApi.quality(window, signal).then((data) => normalizeQuality(data, window)))
const { data, error, loading, readAt } = resource
const windowDays = computed({ get: () => WINDOW_OPTIONS.some((o) => o.value === route.query.window) ? route.query.window : '0', set: (value) => router.replace({ query: { ...route.query, window: value } }) })
watch(windowDays, (value) => resource.load(value), { immediate: true })
onBeforeUnmount(resource.dispose)
const axis = ref('rate'), selectedMetric = ref(null)
watch(windowDays, () => { selectedMetric.value = null })
let metricTrigger
function inspect(metric, trigger) { selectedMetric.value = metric; metricTrigger = trigger }
function restoreMetricFocus() { if (metricTrigger?.isConnected) metricTrigger.focus() }
const routeSeries = computed(() => ['auto', 'shadow'].map((key, index) => ({ name: index ? '影子路由对比' : '自动知识路由', values: (data.value?.trend || []).map((p) => p[`${key}${axis.value === 'rate' ? 'SuccessRate' : 'Confidence'}`]) })))
const docSeries = computed(() => [{ name: '解析', values: (data.value?.processing?.items || []).map((p) => p.averageParseMs) }, { name: '索引', values: (data.value?.processing?.items || []).map((p) => p.averageIndexMs) }])
const channelColumns = [{ key: 'recalledCount', name: '召回' }, { key: 'selectedCount', name: '选入' }, { key: 'selectionRate', name: '选入率', type: 'rate' }, { key: 'resolutionRate', name: '来源解析率', type: 'rate' }, { key: 'snapshotCount', name: '快照' }, { key: 'executionCount', name: '执行' }]
</script>
<template>
  <section class="admin-next-overview-page" aria-labelledby="quality-title">
    <div class="admin-next-overview-page-head"><h1 id="quality-title">知识运行全景</h1><div class="admin-next-overview-actions"><label for="admin-quality-window" class="chat-next-sr-only">统计时间窗</label><select id="admin-quality-window" v-model="windowDays" class="admin-next-overview-window"><option v-for="option in WINDOW_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option></select><Button :loading="loading" :disabled="loading" @click="resource.load(windowDays)"><template #icon><ArrowPathIcon class="admin-next-icon" aria-hidden="true" /></template>刷新</Button></div></div>
    <Alert v-if="error" type="error" show-icon :message="data ? '刷新失败，仍显示上次结果' : '运行统计读取失败'" :description="error"><template #action><Button size="small" :disabled="loading" @click="resource.load(windowDays)">重试运行统计</Button></template></Alert>
    <Skeleton v-if="loading && !data" active :paragraph="{ rows: 6 }" aria-label="正在读取运行统计" />
    <template v-if="data">
      <div class="admin-next-overview-meta"><span>{{ data.windowLabel }}</span><span v-if="readAt">读取于 {{ readAt }}</span><span>{{ data.comparable ? '对比上一等长窗口' : '无可比上一期' }}</span><span v-if="data.truncated">日序列已截断，仅显示接口返回区间</span></div>
      <MetricStrip v-if="data.metrics.length" :metrics="data.metrics" prefix="quality" @inspect="inspect" />
      <Empty v-if="!data.hasData" description="当前时间窗暂无运行记录" />
      <template v-else>
        <section class="admin-next-overview-panel" aria-labelledby="route-trend-title"><div class="admin-next-overview-panel-head"><h2 id="route-trend-title">路由趋势</h2><RadioGroup v-model:value="axis" name="route-trend-axis" aria-label="趋势指标"><RadioButton value="rate">成功率</RadioButton><RadioButton value="confidence">置信度</RadioButton></RadioGroup></div>
          <div class="admin-next-overview-panel-body"><OverviewChart v-if="data.trend.length" :series="routeSeries" :labels="data.trend.map((p) => p.label)" :axis="axis" :label="axis === 'rate' ? '路由成功率' : '路由置信度'" /><Empty v-else description="暂无日序列" />
            <details v-if="data.trend.length" class="admin-next-overview-details"><summary>查看路由数据表</summary><div class="admin-next-overview-table-scroll" role="region" aria-label="路由逐日数据" tabindex="0"><table><caption class="chat-next-sr-only">路由逐日数据</caption><thead><tr><th scope="col">日期</th><th scope="col">自动次数</th><th scope="col">成功率</th><th scope="col">置信度</th><th scope="col">影子次数</th><th scope="col">成功率</th><th scope="col">置信度</th></tr></thead><tbody><tr v-for="p in data.trend" :key="p.date"><th scope="row">{{ p.label }}</th><td>{{ valueText(p.autoCount) }}</td><td>{{ valueText(p.autoSuccessRate, 'rate') }}</td><td>{{ valueText(p.autoConfidence, 'score') }}</td><td>{{ valueText(p.shadowCount) }}</td><td>{{ valueText(p.shadowSuccessRate, 'rate') }}</td><td>{{ valueText(p.shadowConfidence, 'score') }}</td></tr></tbody></table></div></details>
          </div>
        </section>
        <section class="admin-next-overview-panel" aria-labelledby="channels-title"><div class="admin-next-overview-panel-head"><h2 id="channels-title">检索通道</h2><span class="admin-next-overview-note" title="召回、选入和快照来自各自执行计数器，不直接比较不同通道的原始分数。">执行计数</span></div><Empty v-if="!data.channels.length" description="暂无通道记录" /><div v-else class="admin-next-overview-table-scroll" role="region" aria-label="检索通道统计" tabindex="0"><table><caption class="chat-next-sr-only">检索通道统计</caption><thead><tr><th scope="col">通道</th><th v-for="column in channelColumns" :key="column.key" scope="col">{{ column.name }}</th></tr></thead><tbody><tr v-for="channel in data.channels" :key="channel.key"><th scope="row">{{ channel.name }}</th><td v-for="column in channelColumns" :key="column.key">{{ valueText(channel[column.key], column.type) }}</td></tr></tbody></table></div></section>
        <div class="admin-next-overview-columns">
          <section class="admin-next-overview-panel" aria-labelledby="decision-title"><div class="admin-next-overview-panel-head"><h2 id="decision-title">路由结果</h2><span class="admin-next-overview-note">总量 {{ valueText(data.decision?.total) }}</span></div><Empty v-if="!data.decision" description="裁决统计未记录" /><dl v-else class="admin-next-overview-definition"><div><dt>成功</dt><dd>{{ valueText(data.decision.success) }}</dd></div><div><dt>低置信</dt><dd>{{ valueText(data.decision.lowConfidence) }}</dd></div><div><dt>失败</dt><dd>{{ valueText(data.decision.failed) }}</dd></div><div><dt>成功率</dt><dd>{{ valueText(data.decision.successRate, 'rate') }}</dd></div></dl><p v-if="data.decision?.total > 0 && data.decision.total < 30" class="admin-next-overview-panel-note">仅 {{ data.decision.total }} 次路由，比率仅供参考</p></section>
          <section class="admin-next-overview-panel" aria-labelledby="duration-title"><div class="admin-next-overview-panel-head"><h2 id="duration-title">文档处理耗时</h2><span v-if="data.processing?.indexToParseRatio !== null && data.processing" class="admin-next-overview-note">索引 / 解析 {{ data.processing.indexToParseRatio }} 倍</span></div><Empty v-if="!data.processing" description="暂无处理统计" /><div v-else class="admin-next-overview-panel-body"><dl class="admin-next-overview-duration"><div><dt>平均解析</dt><dd>{{ durationText(data.processing.averageParseMs) }}<small>{{ valueText(data.processing.parseCount) }} 次</small></dd></div><div><dt>平均索引</dt><dd>{{ durationText(data.processing.averageIndexMs) }}<small>{{ valueText(data.processing.indexCount) }} 次</small></dd></div></dl></div></section>
        </div>
        <section v-if="data.processing?.items.length" class="admin-next-overview-panel" aria-labelledby="duration-trend-title"><div class="admin-next-overview-panel-head"><h2 id="duration-trend-title">逐日处理耗时</h2></div><div class="admin-next-overview-panel-body"><OverviewChart :series="docSeries" :labels="data.processing.items.map((p) => p.label)" axis="duration" kind="bar" label="文档平均处理耗时" /><details class="admin-next-overview-details"><summary>查看耗时数据表</summary><div class="admin-next-overview-table-scroll" role="region" aria-label="逐日处理耗时数据" tabindex="0"><table><caption class="chat-next-sr-only">逐日处理耗时数据</caption><thead><tr><th scope="col">日期</th><th scope="col">解析次数</th><th scope="col">平均解析</th><th scope="col">索引次数</th><th scope="col">平均索引</th></tr></thead><tbody><tr v-for="p in data.processing.items" :key="p.date"><th scope="row">{{ p.label }}</th><td>{{ valueText(p.parseCount) }}</td><td>{{ durationText(p.averageParseMs) }}</td><td>{{ valueText(p.indexCount) }}</td><td>{{ durationText(p.averageIndexMs) }}</td></tr></tbody></table></div></details></div></section>
      </template>
      <div class="admin-next-overview-related"><a :href="legacyAdminLink('/admin/observability')" target="_blank" rel="noopener noreferrer">旧版对话观测 ↗</a></div>
    </template>
    <Modal :open="Boolean(selectedMetric)" :title="selectedMetric ? `${selectedMetric.label} · 逐日数据` : ''" :footer="null" :get-container="popupContainer" centered destroy-on-close @cancel="selectedMetric = null" @after-close="restoreMetricFocus"><div v-if="selectedMetric" class="admin-next-overview-table-scroll"><p v-if="selectedMetric.hint" class="admin-next-overview-note">{{ selectedMetric.hint }}</p><table><caption class="chat-next-sr-only">指标逐日数据</caption><thead><tr><th scope="col">日期</th><th scope="col">数值</th></tr></thead><tbody><tr v-for="(p, index) in selectedMetric.history" :key="`${p.date}-${index}`"><th scope="row">{{ p.date }}</th><td>{{ valueText(p.value, selectedMetric.valueType) }}</td></tr></tbody></table></div></Modal>
  </section>
</template>
