<script setup>
import { computed,inject,nextTick,onBeforeUnmount,onMounted,ref } from 'vue'
import { Button,Empty,Modal } from 'ant-design-vue'
import { useRoute,useRouter } from 'vue-router'
import { useDocumentRead } from './documentState.js'
import { remembered,useBrowseMemory } from './browseMemory.js'
import ReadState from './ReadState.vue'
import Facts from './Facts.vue'
const props=defineProps({documentId:String,taskId:String,memory:{type:Object,default:()=>({})}})
const route=useRoute(),router=useRouter(),popupContainer=inject('admin-next-container')
const state=useDocumentRead('chunks'),detail=useDocumentRead('chunk'),selected=ref(''),history=ref([])
const page=remembered(props.memory,'page',Math.max(1,Number(route.query.chunkPage)||1)),size=ref(20)
const restoreScroll=useBrowseMemory(props.memory,{page})
const position=computed(()=>(state.data.value?.records||[]).findIndex(row=>row.chunkId===selected.value))
let alive=true,trigger
function read(){return state.read({documentId:props.documentId,taskId:props.taskId||undefined,pageNo:page.value,pageSize:size.value})}
function change(delta){page.value+=delta;router.replace({query:{...route.query,chunkPage:String(page.value)}});read()}
async function open(id,event){
  const wasOpen=Boolean(selected.value)
  if(event){trigger=event.currentTarget;history.value=[]}
  selected.value=id
  const pending=detail.read({documentId:props.documentId,taskId:props.taskId||state.data.value?.taskId||undefined,chunkId:id})
  await nextTick()
  if(alive&&wasOpen&&selected.value===id)document.querySelector('.admin-next-chunk-dialog')?.focus()
  await pending
}
function follow(id){if(selected.value&&selected.value!==id)history.value=[...history.value,selected.value];open(id)}
function previous(){const entries=[...history.value],id=entries.pop();history.value=entries;if(id)open(id)}
function step(delta){const row=state.data.value?.records?.[position.value+delta];if(row)follow(row.chunkId)}
function restoreFocus(){requestAnimationFrame(()=>{if(alive&&trigger?.isConnected)trigger.focus({preventScroll:true})})}
function close(){selected.value='';history.value=[]}
onBeforeUnmount(()=>{alive=false})
onMounted(async()=>{await read();restoreScroll();if(typeof route.query.chunkId==='string')open(route.query.chunkId)})
</script>
<template>
  <section class="admin-next-doc-panel">
    <div class="admin-next-doc-row"><h2>分块</h2><Button :disabled="state.loading.value" @click="read">刷新分块</Button></div>
    <ReadState :state="state" label="分块" @retry="read" />
    <Empty v-if="state.data.value&&!state.data.value.records?.length" description="暂无分块" />
    <ul class="admin-next-doc-items"><li v-for="row in state.data.value?.records || []" :key="row.chunkId"><div><strong>{{ row.title || `分块 ${row.chunkNo}` }}</strong><span class="admin-next-doc-muted">{{ row.sectionPath }} · 父块 {{ row.parentBlockNo ?? '—' }} · {{ row.vectorStatusName || '向量状态未记录' }}</span><p>{{ row.chunkText?.slice(0,240) }}</p></div><Button @click="open(row.chunkId,$event)">分块详情</Button></li></ul>
    <nav v-if="state.data.value" class="admin-next-doc-toolbar" aria-label="分块分页"><Button :disabled="page<=1||state.loading.value" @click="change(-1)">上一页分块</Button><span>第 {{ page }} 页 · 共 {{ state.data.value.total ?? '—' }} 条</span><Button :disabled="page*size>=Number(state.data.value.total)||state.loading.value" @click="change(1)">下一页分块</Button></nav>
    <Modal wrap-class-name="admin-next-chunk-dialog" :open="Boolean(selected)" title="分块详情" :width="900" :get-container="popupContainer" centered destroy-on-close @cancel="close" @after-close="restoreFocus">
      <ReadState :state="detail" label="分块详情" @retry="open(selected)" />
      <template v-if="detail.data.value">
        <div class="admin-next-doc-row"><strong>{{ detail.data.value.chunk?.title || `分块 ${detail.data.value.chunk?.chunkNo ?? ''}` }}</strong><Button v-if="history.length" :disabled="detail.loading.value" @click="previous">返回上一分块</Button></div>
        <h3>分块正文</h3><p class="admin-next-doc-content">{{ detail.data.value.chunk?.chunkText || '未记录正文' }}</p>
        <details><summary>查看父块</summary><p class="admin-next-doc-content">{{ detail.data.value.parentBlock?.parentText || '未记录父块正文' }}</p><Facts :value="detail.data.value.parentBlock" /></details>
        <h3>同父块分块</h3><div class="admin-next-doc-toolbar"><Button v-for="row in detail.data.value.siblingChunks || []" :key="row.chunkId" :disabled="row.chunkId===selected||detail.loading.value" @click="follow(row.chunkId)">{{ row.title || `分块 ${row.chunkNo}` }}</Button></div>
        <details><summary>定位与元数据</summary><Facts :value="Object.fromEntries(Object.entries(detail.data.value.chunk||{}).filter(([key])=>key!=='chunkText'))" /></details>
      </template>
      <template #footer><div class="admin-next-doc-modal-footer"><Button @click="close">返回分块列表</Button><span class="admin-next-doc-muted">{{ position<0?'关联分块':`本页 ${position+1} / ${state.data.value?.records?.length||0}` }}</span><div class="admin-next-doc-toolbar"><Button :disabled="position<=0||detail.loading.value" @click="step(-1)">上一个分块</Button><Button :disabled="position<0||position>=(state.data.value?.records?.length||0)-1||detail.loading.value" @click="step(1)">下一个分块</Button></div></div></template>
    </Modal>
  </section>
</template>
