<script setup>
import {computed,inject,nextTick,onBeforeUnmount,onMounted,ref} from 'vue'
import {useRoute} from 'vue-router'
import {Button,Card,Descriptions,DescriptionsItem,Empty,Modal,Tag,Timeline,TimelineItem} from 'ant-design-vue'
import {createSessionMonitor} from './observabilityState.js'
import {observationApi} from './observabilityApi.js'
import {listQuery,modeLabel,pageNumber,statusLabel,validIdentity} from './observabilityModel.js'
import ReadState from '../document/ReadState.vue'
import ObservationSummary from './ObservationSummary.vue'
import {observationDuration,observationStatusColor,observationStatusStyle,observationTime} from './observationPresentation.js'
import {ArrowPathIcon} from '@heroicons/vue/24/outline'
import {observationPreview} from './observationPreview.js'
import './observability.css'
const props=defineProps({conversationId:String}),route=useRoute(),container=inject('admin-next-container')
const state=createSessionMonitor((id,signal)=>observationApi.read('session',{conversationId:id},signal)),confirm=ref(false),busy=ref(false),writeError=ref(''),notice=ref(''),controller=new AbortController();const summaryTrigger=ref(null);let alive=true,trigger
onBeforeUnmount(()=>{alive=false;controller.abort();state.dispose()});onMounted(()=>state.start(props.conversationId))
const rows=computed(()=>state.data.value?.exchanges||[]),page=computed(()=>Math.min(pageNumber(route.query.turnPage),Math.max(1,Math.ceil(rows.value.length/20)))),visible=computed(()=>rows.value.slice((page.value-1)*20,page.value*20))
function open(event){trigger=event.currentTarget;confirm.value=true;nextTick(()=>{if(alive)document.querySelector('.admin-next-observe-summary-dialog')?.focus()})}
function restore(){requestAnimationFrame(()=>{if(alive){const element=summaryTrigger.value?.$el||trigger;if(element?.isConnected)element.focus()}})}
async function rebuild(){if(busy.value)return;busy.value=true;writeError.value='';try{const summary=await observationApi.rebuild(props.conversationId,controller.signal);if(alive){state.data.value={...state.data.value,memorySummary:summary};notice.value='摘要已重建';confirm.value=false}}catch(e){if(alive)writeError.value=e.message}finally{if(alive)busy.value=false}}
</script>
<template>
<section class="admin-next-observe admin-next-observe--session">
 <RouterLink class="admin-next-observe-back" :to="{name:'AdminObservationList',query:{...listQuery($route.query),focus:$route.query.listAnchor}}">← 返回会话列表</RouterLink>
 <header class="admin-next-observe-page-heading"><div class="admin-next-observe-toolbar"><h1>会话链路</h1><Tag v-if="state.data.value" :color="state.data.value.running?'processing':'default'">{{ state.data.value.running?'进行中':'空闲' }}</Tag></div><div class="admin-next-observe-toolbar"><Button v-if="state.data.value?.running" @click="state.pause()">暂停自动刷新</Button><Button :loading="state.loading.value" @click="state.start(conversationId)"><template #icon><ArrowPathIcon class="admin-next-icon" aria-hidden="true" /></template>刷新会话</Button></div></header>
 <ReadState :state="state" label="会话" @retry="state.start(conversationId)" />
 <template v-if="state.data.value">
  <p v-if="notice" role="status">{{ notice }}</p>
  <div class="admin-next-observe-session-layout">
   <Card class="admin-next-observe-rounds" ><template #title><h2>会话轮次</h2></template><template #extra><Tag>{{ rows.length }} 轮</Tag></template>
    <Timeline v-if="visible.length" class="admin-next-observe-timeline"><TimelineItem v-for="(exchange,index) in visible" :key="exchange.exchangeId"><template #dot><span class="admin-next-observe-turn-dot">{{ (page-1)*20+index+1 }}</span></template><article class="admin-next-observe-round"><div class="admin-next-observe-row-heading"><Tag :color="observationStatusColor(exchange.status)" :style="observationStatusStyle(exchange.status)">{{ statusLabel(exchange.status) }}</Tag><div class="admin-next-observe-row-meta"><span>{{ observationDuration(exchange.totalResponseTimeMs) }}</span><time v-if="exchange.createTime">{{ observationTime(exchange.createTime) }}</time></div></div><h3>{{ exchange.question||'未记录问题' }}</h3><p v-if="exchange.answer" class="admin-next-observe-round-preview">{{ observationPreview(exchange.answer,160) }}</p><RouterLink v-if="validIdentity(exchange.exchangeId)" class="admin-next-observe-round-link" :to="{name:'AdminObservationExchange',params:{conversationId,exchangeId:validIdentity(exchange.exchangeId)},query:{...$route.query,turnPage:String(page)}}">查看轮次详情 →</RouterLink><p v-else role="status">轮次身份无法安全读取。</p></article></TimelineItem></Timeline><Empty v-else description="暂无轮次" />
    <nav class="admin-next-observe-toolbar admin-next-observe-pager" aria-label="轮次分页"><span class="admin-next-observe-muted">第 {{ page }} 页 · {{ rows.length }} 轮</span><div class="admin-next-observe-toolbar"><RouterLink v-if="page>1" :to="{query:{...$route.query,turnPage:String(page-1)}}">上一页轮次</RouterLink><RouterLink v-if="page*20<rows.length" :to="{query:{...$route.query,turnPage:String(page+1)}}">下一页轮次</RouterLink></div></nav>
   </Card>
   <aside class="admin-next-observe-session-aside" aria-label="会话信息与摘要">
    <Card title="长期摘要" size="small"><template #extra><Button ref="summaryTrigger" size="small" type="text" :disabled="busy||state.loading.value||state.data.value.running" @click="open">重建会话摘要</Button></template><ObservationSummary :summary="state.data.value.memorySummary" /></Card>
    <Card title="会话信息" size="small"><Descriptions :column="1" size="small" layout="vertical"><DescriptionsItem label="会话 ID"><span class="admin-next-observe-identifier">{{ conversationId }}</span></DescriptionsItem><DescriptionsItem v-if="state.data.value.chatMode" label="模式">{{ modeLabel(state.data.value.chatMode) }}</DescriptionsItem><DescriptionsItem label="消息数">{{ state.data.value.messageCount??'—' }}</DescriptionsItem><DescriptionsItem v-if="state.data.value.updatedAt" label="更新时间">{{ observationTime(state.data.value.updatedAt) }}</DescriptionsItem></Descriptions></Card>
   </aside>
  </div>
 </template>
 <Modal wrap-class-name="admin-next-observe-summary-dialog" :open="confirm" title="重建会话摘要" :get-container="container" :closable="!busy" :mask-closable="false" :keyboard="!busy" :confirm-loading="busy" :ok-button-props="{disabled:busy}" :cancel-button-props="{disabled:busy}" @ok="rebuild" @cancel="!busy&&(confirm=false)" :after-close="restore"><p>将重新生成当前会话的长期摘要。</p><p>会话 ID：{{ conversationId }}</p><p v-if="writeError" role="alert">{{ writeError }}</p></Modal>
</section>
</template>
