<script setup>
import { computed,inject,nextTick,onBeforeUnmount,reactive,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { Button,Input,Modal,Empty } from 'ant-design-vue'
import { routeApi } from './routeApi.js'
import { createReadResource } from '../dashboard/overviewState.js'
import TraceDetails from './TraceDetails.vue'
import { routeStatus } from './routeModel.js'
import ReadState from '../document/ReadState.vue'
import './route.css'
const route=useRoute(),router=useRouter(),container=inject('admin-next-container'),filters=reactive({conversationId:route.query.conversationId||'',mode:route.query.mode||'',routeStatus:route.query.routeStatus||''})
const state=createReadResource((key,signal)=>routeApi.read('traces',JSON.parse(key),signal));onBeforeUnmount(state.dispose)
const selected=computed(()=>(state.data.value?.records||[]).find(x=>x.id===route.query.traceId)),page=computed(()=>Math.max(1,Number(route.query.page)||1))
function load(){state.load(JSON.stringify({conversationId:route.query.conversationId||'',mode:route.query.mode||'',routeStatus:route.query.routeStatus||'',pageNo:page.value,pageSize:'20'}))}
function search(){router.push({query:{...filters,page:'1'}})}
watch(()=>[route.query.conversationId,route.query.mode,route.query.routeStatus],()=>Object.assign(filters,{conversationId:route.query.conversationId||'',mode:route.query.mode||'',routeStatus:route.query.routeStatus||''}))
watch(()=>[route.query.conversationId,route.query.mode,route.query.routeStatus,route.query.page],load,{immediate:true})
let trigger,alive=true
onBeforeUnmount(()=>{alive=false})
function open(item,event){trigger=event.currentTarget;router.push({query:{...route.query,traceId:item.id}})}
function restoreFocus(){requestAnimationFrame(()=>{if(alive&&trigger?.isConnected)trigger.focus({preventScroll:true})})}
function close(){router.replace({query:{...route.query,traceId:undefined}})}
watch(selected,async value=>{if(value){await nextTick();if(alive&&selected.value===value)document.querySelector('.admin-next-route-trace-dialog')?.focus()}})
</script>
<template><section class="admin-next-route"><h1>路由追踪</h1><form @submit.prevent="search"><label for="trace-conversation">会话 ID</label><Input id="trace-conversation" v-model:value="filters.conversationId" /><label for="trace-mode">模式</label><select id="trace-mode" v-model="filters.mode"><option value="">全部模式</option><option value="auto">auto</option><option value="shadow">shadow</option></select><label for="trace-status">状态</label><select id="trace-status" v-model="filters.routeStatus"><option value="">全部状态</option><option value="1">成功</option><option value="2">低置信</option><option value="3">失败</option></select><Button html-type="submit" type="primary">筛选</Button><Button @click="load">刷新追踪</Button></form><ReadState :state="state" label="路由追踪" @retry="load" /><section><ul><li v-for="item in state.data.value?.records||[]" :key="item.id"><Button type="link" @click="open(item,$event)">{{ item.question||item.id }}</Button><p>{{ item.mode||'未记录模式' }} · 状态 {{ routeStatus(item.routeStatus) }} · 置信度 {{ item.confidence??'未记录' }}</p></li></ul><Empty v-if="state.data.value&&!state.data.value.records?.length" description="暂无追踪记录" /><nav aria-label="追踪分页"><Button :disabled="page<=1||state.loading.value" @click="router.push({query:{...route.query,page:String(page-1),traceId:undefined}})">上一页</Button><span>第 {{ page }} 页 · 共 {{ state.data.value?.totalSize??'—' }} 条</span><Button :disabled="page>=Number(state.data.value?.totalPages||0)||state.loading.value" @click="router.push({query:{...route.query,page:String(page+1),traceId:undefined}})">下一页</Button></nav></section><Modal wrap-class-name="admin-next-route-trace-dialog" @after-close="restoreFocus" :width="860" :open="Boolean(selected)" title="追踪详情" :get-container="container" :footer="null" @cancel="close"><TraceDetails v-if="selected" :record="selected" /><p>未返回的字段表示未记录。</p></Modal></section></template>



