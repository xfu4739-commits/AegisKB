<script setup>
import {computed,nextTick,reactive,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {Button,Card,Empty,Grid,Input,Statistic,Table,Tag,Tooltip} from 'ant-design-vue'
import {ArrowPathIcon,ChatBubbleLeftRightIcon,ClockIcon,MagnifyingGlassIcon,RectangleStackIcon} from '@heroicons/vue/24/outline'
import {useObservationRead} from './observabilityState.js'
import {listQuery,modeLabel,statusLabel,pageNumber,validIdentity} from './observabilityModel.js'
import {observationStatusColor,observationStatusStyle,observationTime} from './observationPresentation.js'
import ReadState from '../document/ReadState.vue'
import {observationPreview} from './observationPreview.js'
import './observability.css'
const route=useRoute(),router=useRouter(),state=useObservationRead('list'),filters=reactive({keyword:'',mode:'ALL',status:'ALL'}),screens=Grid.useBreakpoint()
const wide=computed(()=>screens.value.xl===true),page=computed(()=>pageNumber(route.query.listPage)),sessions=computed(()=>state.data.value?.sessions||[])
const modeOptions=[{value:'ALL',label:'全部模式'},...['OPEN_CHAT','AUTO_DOCUMENT','DOCUMENT'].map(value=>({value,label:modeLabel(value)}))]
const statusOptions=[{value:'ALL',label:'全部状态'},...['RUNNING','COMPLETED','FAILED','STOPPED'].map(value=>({value,label:statusLabel(value)}))]
const columns=[{title:'会话',key:'session'},{title:'模式',key:'mode',width:145},{title:'状态',key:'status',width:105},{title:'消息',key:'count',width:75},{title:'更新时间',key:'updated',width:165},{title:'操作',key:'action',width:110}]
async function load(){await state.read({keyword:route.query.listKeyword||'',chatMode:route.query.listMode||'ALL',turnStatus:route.query.listStatus||'ALL',pageNo:String(page.value),pageSize:'20'});await nextTick();if(typeof route.query.focus==='string'){const target=document.getElementById('obs-session-'+route.query.focus);target?.focus();target?.scrollIntoView({block:'center'})}}
function search(){router.push({query:{listKeyword:filters.keyword.trim(),listMode:filters.mode,listStatus:filters.status,listPage:'1'}})}
async function open(session){const id=validIdentity(session.conversationId);if(!id)return;await router.replace({query:{...route.query,focus:id}});router.push({name:'AdminObservationSession',params:{conversationId:id},query:{...listQuery(route.query),listAnchor:id}})}
watch(()=>[route.query.listKeyword,route.query.listMode,route.query.listStatus,route.query.listPage],()=>{Object.assign(filters,{keyword:route.query.listKeyword||'',mode:route.query.listMode||'ALL',status:route.query.listStatus||'ALL'});load()},{immediate:true})
</script>
<template>
<section class="admin-next-observe admin-next-observe--list">
 <header class="admin-next-observe-page-heading"><div><h1>对话观测</h1></div><Button :loading="state.loading.value" @click="load"><template #icon><ArrowPathIcon class="admin-next-icon" aria-hidden="true" /></template>刷新会话列表</Button></header>
 <div class="admin-next-observe-metrics" aria-label="会话统计">
  <Card size="small"><div class="admin-next-observe-metric"><Statistic title="当前筛选" :value="state.data.value?.totalSize??'—'" :value-style="{fontSize:'var(--admin-metric-size)',fontWeight:600}" /><span class="admin-next-observe-metric-icon"><ChatBubbleLeftRightIcon /></span></div></Card>
  <Card size="small"><div class="admin-next-observe-metric"><Statistic title="本页会话" :value="state.data.value?sessions.length:'—'" :value-style="{fontSize:'var(--admin-metric-size)',fontWeight:600}" /><span class="admin-next-observe-metric-icon"><RectangleStackIcon /></span></div></Card>
  <Card size="small"><div class="admin-next-observe-metric"><Statistic title="本页进行中" :value="state.data.value?sessions.filter(x=>x.running===true).length:'—'" :value-style="{fontSize:'var(--admin-metric-size)',fontWeight:600}" /><span class="admin-next-observe-metric-icon"><ClockIcon /></span></div></Card>
 </div>
 <ReadState :state="state" label="会话列表" @retry="load" />
 <Card class="admin-next-observe-list-card" title="会话列表" :body-style="{padding:'0'}"><template #extra><span class="admin-next-observe-muted">{{ sessions.length }} 条本页记录</span></template>
  <form class="admin-next-observe-filter" @submit.prevent="search">
   <label for="obs-search" class="chat-next-sr-only">搜索会话</label><Input id="obs-search" v-model:value="filters.keyword" allow-clear placeholder="搜索会话 ID、问题或回答"><template #prefix><MagnifyingGlassIcon class="admin-next-icon admin-next-observe-muted" /></template></Input>
   <div><label for="obs-mode">模式</label><select id="obs-mode" v-model="filters.mode"><option v-for="option in modeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></div>
   <div><label for="obs-status">状态</label><select id="obs-status" v-model="filters.status"><option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></div><Button html-type="submit" type="primary">筛选</Button>
  </form>
  <Table v-if="wide" class="admin-next-observe-table" :columns="columns" :data-source="sessions" row-key="conversationId" :pagination="false" :table-layout="'fixed'" :locale="{emptyText:'暂无匹配会话'}">
   <template #bodyCell="{column,record}">
    <template v-if="column.key==='session'"><Tooltip :title="record.conversationId"><Button :id="'obs-session-'+record.conversationId" type="link" class="admin-next-observe-session-name" :disabled="!validIdentity(record.conversationId)" @click="open(record)">{{ observationPreview(record.latestUserMessage)||record.conversationId }}</Button></Tooltip></template>
    <span v-else-if="column.key==='mode'" class="admin-next-observe-muted">{{ modeLabel(record.chatMode) }}</span>
    <Tag v-else-if="column.key==='status'" :color="observationStatusColor(record.latestTurnStatus)" :style="observationStatusStyle(record.latestTurnStatus)">{{ statusLabel(record.latestTurnStatus) }}</Tag>
    <span v-else-if="column.key==='count'">{{ record.messageCount??'—' }}</span>
    <span v-else-if="column.key==='updated'" class="admin-next-observe-date">{{ observationTime(record.updatedAt) }}</span>
    <Button v-else-if="column.key==='action'" type="link" :disabled="!validIdentity(record.conversationId)" @click="open(record)">查看链路</Button>
   </template>
  </Table>
  <div v-else class="admin-next-observe-mobile-sessions"><ul class="admin-next-observe-list"><li v-for="record in sessions" :key="record.conversationId"><div class="admin-next-observe-row-heading"><Button :id="'obs-session-'+record.conversationId" type="link" class="admin-next-observe-session-name" :disabled="!validIdentity(record.conversationId)" @click="open(record)">{{ observationPreview(record.latestUserMessage)||record.conversationId }}</Button><Tag :color="observationStatusColor(record.latestTurnStatus)" :style="observationStatusStyle(record.latestTurnStatus)">{{ statusLabel(record.latestTurnStatus) }}</Tag></div><div class="admin-next-observe-row-meta"><span>{{ modeLabel(record.chatMode) }}</span><span>{{ record.messageCount??'—' }} 条消息</span><time>{{ observationTime(record.updatedAt) }}</time></div></li></ul><Empty v-if="state.data.value&&!sessions.length" description="暂无匹配会话" /></div>
  <nav class="admin-next-observe-toolbar admin-next-observe-pager" aria-label="会话分页"><span class="admin-next-observe-muted">第 {{ page }} 页</span><div class="admin-next-observe-toolbar"><Button :disabled="page<=1||state.loading.value" @click="router.push({query:{...listQuery(route.query),listPage:String(page-1)}})">上一页会话</Button><Button :disabled="page>=Number(state.data.value?.totalPages||0)||state.loading.value" @click="router.push({query:{...listQuery(route.query),listPage:String(page+1)}})">下一页会话</Button></div></nav>
 </Card>
</section>
</template>
