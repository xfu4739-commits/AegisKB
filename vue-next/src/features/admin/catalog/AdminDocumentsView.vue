<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { Alert, Button, Empty, Input, Modal, Skeleton } from 'ant-design-vue'
import { catalogApi } from './catalogApi.js'
import { dateText,documentBusy,fileSize,normalizeBases,normalizeDocumentPage,readListQuery,uploadForm } from './catalogModel.js'
import { createReadResource } from '../dashboard/overviewState.js'
import { createMutation } from './catalogState.js'
import DocumentUpload from './DocumentUpload.vue'
import './catalog.css'
const route=useRoute(),router=useRouter(),popupContainer=inject('admin-next-container')
const query=computed(()=>readListQuery(route.query)),keyword=ref(query.value.keyword)
const resource=createReadResource((key,signal)=>catalogApi.documents(JSON.parse(key),signal).then(normalizeDocumentPage))
const basesResource=createReadResource((_,signal)=>catalogApi.bases(signal).then(normalizeBases)), mutation=createMutation()
const {data,error,loading}=resource,{data:bases,error:basesError,loading:basesLoading}=basesResource,{pending,error:actionError}=mutation
const read=()=>resource.load(JSON.stringify(query.value))
watch(query,()=>{keyword.value=query.value.keyword;read()},{immediate:true})
const totalPages=computed(()=>Math.max(1,Math.ceil((data.value?.total || 0)/query.value.size)))
watch(()=>data.value,page=>{if(page && query.value.page>totalPages.value)router.replace({query:{...route.query,page:totalPages.value===1?undefined:String(totalPages.value)}})})
function setQuery(page=1,q=query.value.keyword,size=query.value.size){return router.push({query:{...route.query,q:q||undefined,page:page===1?undefined:String(page),size:String(size)}})}
const uploadOpen=ref(false),draft=ref({}),target=ref(null),notice=ref(''),uploaded=ref(null)
let trigger
function restoreFocus(){if(trigger?.isConnected)trigger.focus();else document.getElementById('catalog-document-search')?.focus()}
function openUpload(event){trigger=event.currentTarget;draft.value={knowledgeBaseId:'',documentName:'',operatorId:'',file:null};actionError.value='';uploadOpen.value=true;basesResource.load()}
function remove(item,event){trigger=event.currentTarget;target.value={...item};actionError.value=''}
function detailLink(item,taskId){return router.resolve({name:'AdminDocumentDetail',params:{documentId:item.documentId},query:{listQ:query.value.keyword||undefined,listPage:String(query.value.page),listSize:String(query.value.size),...(taskId?{parseTaskId:taskId,showParseProgress:'1'}:{})}}).href}
function upload(){let form;try{if(basesError.value)throw new Error('请先重新读取知识库选项。');form=uploadForm(draft.value,bases.value || [])}catch(e){actionError.value=e.message;return}mutation.run(signal=>catalogApi.upload(form,signal),async result=>{if(typeof result?.documentId!=='string' || typeof result?.taskId!=='string')throw new Error('提交响应缺少文档或任务标识，请刷新列表核对，勿重复提交。');uploaded.value=result;uploadOpen.value=false;draft.value={};notice.value='已提交解析任务，解析和索引尚未完成';if(query.value.page===1 && !query.value.keyword)await read();else await setQuery(1,'')})}
function confirmDelete(){const row=target.value;if(documentBusy(row)){actionError.value='当前文档有进行中的任务，不能删除。';return}mutation.run(signal=>catalogApi.deleteDocument(row.documentId,signal),async result=>{if(!result || String(result.documentId)!==row.documentId)throw new Error('删除响应不匹配，请刷新核对。');target.value=null;notice.value='文档删除请求已完成';if(data.value?.records.length===1 && query.value.page>1)await setQuery(query.value.page-1);else await read()})}
onMounted(()=>basesResource.load());onBeforeUnmount(()=>{resource.dispose();basesResource.dispose();mutation.dispose()})
</script>
<template>
  <section class="admin-next-catalog-page" aria-labelledby="documents-title">
    <div class="admin-next-catalog-head"><h1 id="documents-title">文档管理</h1><Button type="primary" :disabled="pending" @click="openUpload">上传文档</Button></div>
    <div v-if="notice" class="admin-next-catalog-notice" role="status">{{ notice }}<template v-if="uploaded"><span class="admin-next-catalog-id">文档 {{ uploaded.documentId }} · 任务 {{ uploaded.taskId }}</span><RouterLink :to="detailLink(uploaded,uploaded.taskId)">任务详情 →</RouterLink></template></div>
    <form class="admin-next-catalog-filters" role="search" aria-label="文档筛选" @submit.prevent="setQuery(1,keyword.trim())"><label for="catalog-document-search" class="chat-next-sr-only">搜索文档</label><Input id="catalog-document-search" v-model:value="keyword" placeholder="文档名称或文件名" /><Button html-type="submit" :disabled="pending">搜索</Button><Button v-if="query.keyword" :disabled="pending" @click="setQuery(1,'')">清除筛选</Button><Button :loading="loading" :disabled="pending || loading" @click="read">刷新</Button></form>
    <Alert v-if="error" type="error" show-icon :message="data?'刷新失败，仍显示上次列表':'文档列表读取失败'" :description="error"><template #action><Button :disabled="loading || pending" @click="read">重试列表</Button></template></Alert>
    <Skeleton v-if="loading && !data" active />
    <template v-if="data"><Empty v-if="!data.records.length" :description="query.keyword?'没有匹配文档':'暂无文档'" />
      <div v-else class="admin-next-catalog-list" aria-label="文档列表"><div class="admin-next-catalog-list-head admin-next-catalog-document-row" aria-hidden="true"><span>文档</span><span>处理状态</span><span>文件与更新时间</span><span>操作</span></div><article v-for="item in data.records" :key="item.documentId" class="admin-next-catalog-document-row"><div><strong class="admin-next-catalog-doc-name">{{ item.documentName || item.originalFileName || '未命名文档' }}</strong><span class="admin-next-catalog-subtle">{{ item.originalFileName || '文件名未记录' }}</span><span class="admin-next-catalog-subtle">{{ item.knowledgeBaseName || '知识库未记录' }}</span><details class="admin-next-catalog-identity"><summary>查看 ID</summary><span class="admin-next-catalog-id">{{ item.documentId }}</span></details></div><div class="admin-next-catalog-stages"><span>解析：{{ item.parseStatusName || '未记录' }}</span><span>策略：{{ item.strategyStatusName || '未记录' }}</span><span>索引：{{ item.indexStatusName || '未记录' }}</span></div><div><span class="admin-next-catalog-subtle">{{ item.fileTypeName || '—' }} · {{ fileSize(item.fileSize) }}</span><time class="admin-next-catalog-subtle">{{ dateText(item.editTime) }}</time></div><div class="admin-next-catalog-row-actions"><RouterLink :to="detailLink(item)">查看详情</RouterLink><Button danger :disabled="pending || loading || documentBusy(item)" :title="documentBusy(item)?'请等待当前任务结束':undefined" :aria-label="`删除文档 ${item.documentName || item.originalFileName}`" @click="remove(item,$event)">删除</Button></div></article></div>
      <nav class="admin-next-catalog-pagination" aria-label="文档分页"><span>共 {{ data.total }} 条 · 第 {{ query.page }} / {{ totalPages }} 页</span><div><label for="document-page-size" class="chat-next-sr-only">每页条数</label><select id="document-page-size" :value="query.size" :disabled="pending" @change="setQuery(1,query.keyword,Number($event.target.value))"><option v-for="size in [12,24,48]" :key="size" :value="size">{{ size }} 条 / 页</option></select><Button :disabled="query.page<=1 || pending || loading" @click="setQuery(query.page-1)">上一页</Button><Button :disabled="query.page>=totalPages || pending || loading" @click="setQuery(query.page+1)">下一页</Button></div></nav>
    </template>
    <DocumentUpload :open="uploadOpen" :draft="draft" :bases="bases || []" :bases-loading="basesLoading" :bases-error="basesError" :pending="pending" :error="actionError" @close="uploadOpen=false" @submit="upload" @retry-bases="basesResource.load()" @after-close="restoreFocus" />
    <Modal :open="Boolean(target)" title="删除文档" ok-text="确认删除" cancel-text="取消" :ok-button-props="{danger:true,loading:pending,disabled:pending}" :cancel-button-props="{disabled:pending}" :closable="!pending" :keyboard="!pending" :mask-closable="!pending" :get-container="popupContainer" centered destroy-on-close @cancel="target=null" @ok="confirmDelete" @after-close="restoreFocus"><template v-if="target"><p>删除“{{ target.documentName || target.originalFileName }}”？</p><p class="admin-next-catalog-id">ID：{{ target.documentId }}</p><p>将清理文档记录、关联索引与存储文件，删除后无法恢复。</p><Alert v-if="actionError" type="error" show-icon :message="actionError" /></template></Modal>
  </section>
</template>
