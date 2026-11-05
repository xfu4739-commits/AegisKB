<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { Alert, Button, Empty, Skeleton } from 'ant-design-vue'
import { ArrowPathIcon } from '@heroicons/vue/24/outline'
import { overviewApi } from './overviewApi.js'
import { createReadResource } from './overviewState.js'
import { summarizeDocuments, summarizeRoutes, valueText } from './overviewModel.js'
import { legacyAdminLink } from './overviewLinks.js'
import MetricStrip from './MetricStrip.vue'
import './overview.css'

const documents = createReadResource((_key, signal) => overviewApi.documents(signal).then(summarizeDocuments))
const routes = createReadResource((_key, signal) => overviewApi.routes(signal).then(summarizeRoutes))
const { data: docs, error: docError, loading: docLoading, readAt: docReadAt } = documents
const { data: routeStats, error: routeError, loading: routeLoading } = routes
const busy = computed(() => docLoading.value || routeLoading.value)
const metrics = computed(() => docs.value ? [
  { key: 'total', label: '文档总量', valueText: valueText(docs.value.total) },
  { key: 'parse', label: '解析成功 · 样本', valueText: valueText(docs.value.parseSuccess) },
  { key: 'strategy', label: '策略确认 · 样本', valueText: valueText(docs.value.strategyConfirmed) },
  { key: 'index', label: '索引完成 · 样本', valueText: valueText(docs.value.indexSuccess) }
] : [])
const rates = computed(() => routeStats.value ? [
  { label: '路由成功率', value: valueText(routeStats.value.successRate, 'rate') },
  { label: '低置信与失败率', value: valueText(routeStats.value.lowFailureRate, 'rate') },
  { label: '平均置信度', value: valueText(routeStats.value.averageConfidence, 'score') },
  { label: '影子命中率', value: valueText(routeStats.value.shadowHitRate, 'rate') }
] : [])
const stages = computed(() => docs.value ? [{ label: '解析成功', value: docs.value.parseSuccess }, { label: '策略确认', value: docs.value.strategyConfirmed }, { label: '索引完成', value: docs.value.indexSuccess }] : [])
function refresh() { return Promise.all([documents.load(), routes.load()]) }
onMounted(refresh)
onBeforeUnmount(() => { documents.dispose(); routes.dispose() })
</script>
<template>
  <section class="admin-next-overview-page" aria-labelledby="dashboard-title">
    <div class="admin-next-overview-page-head"><h1 id="dashboard-title">运营总览</h1><div class="admin-next-overview-actions"><span v-if="docReadAt" class="admin-next-overview-note">读取于 {{ docReadAt }}</span><Button :loading="busy" :disabled="busy" @click="refresh"><template #icon><ArrowPathIcon class="admin-next-icon" aria-hidden="true" /></template>刷新</Button></div></div>
    <Alert v-if="docError" type="error" show-icon :message="docs ? '文档统计刷新失败，仍显示上次结果' : '文档统计读取失败'" :description="docError"><template #action><Button size="small" :disabled="docLoading" @click="documents.load()">重试文档统计</Button></template></Alert>
    <Skeleton v-if="docLoading && !docs" active :paragraph="{ rows: 3 }" aria-label="正在读取文档统计" />
    <MetricStrip v-if="docs" :metrics="metrics" prefix="document" />
    <div class="admin-next-overview-columns">
      <section v-if="docs" class="admin-next-overview-panel" aria-labelledby="processing-title">
        <div class="admin-next-overview-panel-head"><h2 id="processing-title">文档处理</h2><span class="admin-next-overview-note">返回样本 {{ docs.sampleSize }} 条<span v-if="docs.total !== null"> / 总量 {{ valueText(docs.total) }}</span></span></div>
        <Empty v-if="!docs.sampleSize" description="暂无文档样本" />
        <div v-else class="admin-next-overview-panel-body"><div v-for="stage in stages" :key="stage.label" class="admin-next-overview-stage"><div><span>{{ stage.label }}</span><span>{{ stage.value }} / {{ docs.sampleSize }}</span></div><div class="admin-next-overview-bar" aria-hidden="true"><span :style="{ width: `${stage.value / docs.sampleSize * 100}%` }"></span></div></div></div>
      </section>
      <section class="admin-next-overview-panel" aria-labelledby="route-stats-title">
        <div class="admin-next-overview-panel-head"><h2 id="route-stats-title">路由样本</h2><span v-if="routeStats" class="admin-next-overview-note">{{ routeStats.sampleSize }} 条<span v-if="routeStats.total !== null"> / 总量 {{ valueText(routeStats.total) }}</span></span></div>
        <div class="admin-next-overview-panel-body">
          <Alert v-if="routeError" type="error" show-icon :message="routeStats ? '路由统计刷新失败，仍显示上次结果' : '路由统计读取失败'" :description="routeError"><template #action><Button size="small" :disabled="routeLoading" @click="routes.load()">重试路由统计</Button></template></Alert>
          <Skeleton v-if="routeLoading && !routeStats" active :paragraph="{ rows: 3 }" aria-label="正在读取路由统计" />
          <Empty v-else-if="routeStats && !routeStats.sampleSize" description="暂无路由样本" />
          <template v-else-if="routeStats"><dl class="admin-next-overview-route-metrics"><div v-for="metric in rates" :key="metric.label"><dt>{{ metric.label }}</dt><dd>{{ metric.value }}</dd></div></dl><p class="admin-next-overview-note">成功 {{ routeStats.success }} · 低置信 {{ routeStats.lowConfidence }} · 失败 {{ routeStats.failed }}<span v-if="routeStats.unknown"> · 未记录 {{ routeStats.unknown }}</span></p><details class="admin-next-overview-details"><summary>查看统计口径</summary><p>基于本次返回的 {{ routeStats.sampleSize }} 条记录。自动 {{ routeStats.autoCount }}，影子 {{ routeStats.shadowCount }}；置信度均值只使用有记录的值，≥0.8 共 {{ routeStats.highConfidence }} 条；影子命中率只使用已有命中标记。</p></details></template>
        </div>
      </section>
    </div>
    <section v-if="docs?.sampleSize" class="admin-next-overview-panel" aria-labelledby="document-sample-title"><div class="admin-next-overview-panel-head"><h2 id="document-sample-title">文档样本</h2><a :href="legacyAdminLink('/admin/documents')" target="_blank" rel="noopener noreferrer">旧版文档管理 ↗</a></div><ul class="admin-next-overview-doc-list"><li v-for="doc in docs.records.slice(0, 5)" :key="doc.documentId"><div><strong>{{ doc.documentName || doc.originalFileName || '未命名文档' }}</strong><span>{{ doc.knowledgeBaseName || '知识库未记录' }}</span></div><span class="admin-next-overview-status" :class="{ positive: String(doc.indexStatus) === '3', danger: String(doc.indexStatus) === '4' }">{{ doc.indexStatusName || '状态未记录' }}</span></li></ul></section>
    <div class="admin-next-overview-related"><RouterLink to="/admin/quality-overview">知识运行全景 →</RouterLink><a :href="legacyAdminLink('/admin/knowledge-route/traces')" target="_blank" rel="noopener noreferrer">旧版路由追踪 ↗</a><a v-if="!docs?.sampleSize" :href="legacyAdminLink('/admin/documents')" target="_blank" rel="noopener noreferrer">旧版文档管理 ↗</a></div>
  </section>
</template>
