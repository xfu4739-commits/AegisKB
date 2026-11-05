<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Empty, Input, Modal, Skeleton } from 'ant-design-vue'
import { catalogApi } from './catalogApi.js'
import { buildBasePayload, createBaseDraft, normalizeBases, pageSlice, readListQuery } from './catalogModel.js'
import { createReadResource } from '../dashboard/overviewState.js'
import { createMutation } from './catalogState.js'
import BaseEditor from './BaseEditor.vue'
import './catalog.css'
const route=useRoute(),router=useRouter(),popupContainer=inject('admin-next-container')
const resource=createReadResource((_,signal)=>catalogApi.bases(signal).then(normalizeBases)), mutation=createMutation()
const {data,error,loading}=resource,{pending,error:actionError}=mutation
const query=computed(()=>readListQuery(route.query,10)),keyword=ref(query.value.keyword)
watch(()=>query.value.keyword,value=>{keyword.value=value})
const list=computed(()=>pageSlice(data.value||[],query.value.keyword,query.value.page,query.value.size))
watch([data,query],()=>{const page=list.value.page;if(data.value && query.value.page!==page)router.replace({query:{...route.query,page:page===1?undefined:String(page)}})})
function setQuery(page=1,q=query.value.keyword){return router.push({query:{...route.query,q:q||undefined,page:page===1?undefined:String(page),size:String(query.value.size)}})}
const editorOpen=ref(false),mode=ref('view'),draft=ref(null),target=ref(null),notice=ref('')
let trigger
function restoreFocus(){if(trigger?.isConnected)trigger.focus();else document.getElementById('catalog-base-search')?.focus()}
function openEditor(item,nextMode,event){if(pending.value)return;trigger=event.currentTarget;actionError.value='';try{draft.value=createBaseDraft(item);mode.value=nextMode;editorOpen.value=true}catch(e){notice.value=e.message}}
function remove(item,event){if(pending.value)return;trigger=event.currentTarget;target.value={...item};actionError.value=''}
function save(){let payload;try{payload=buildBasePayload(draft.value)}catch(e){actionError.value=e.message;return}mutation.run(signal=>catalogApi.saveBase(payload,signal),async result=>{if(!result || result.baseName==null)throw new Error('保存响应无效，请刷新核对，勿重复提交。');editorOpen.value=false;notice.value='知识库已保存';await resource.load()})}
function confirmDelete(){const id=target.value.id;mutation.run(signal=>catalogApi.deleteBase(id,signal),async result=>{if(result!==true)throw new Error('未能停用知识库，请刷新核对。');target.value=null;notice.value='知识库已停用';await resource.load()})}
onMounted(()=>resource.load());onBeforeUnmount(()=>{resource.dispose();mutation.dispose()})
</script>
<template>
  <section class="admin-next-catalog-page" aria-labelledby="bases-title">
    <div class="admin-next-catalog-head"><h1 id="bases-title">知识库管理</h1><Button type="primary" :disabled="pending || loading" @click="openEditor({},'create',$event)">新建知识库</Button></div>
    <p v-if="notice" role="status" class="admin-next-catalog-notice">{{ notice }}</p>
    <form class="admin-next-catalog-filters" role="search" aria-label="知识库筛选" @submit.prevent="setQuery(1,keyword.trim())"><label for="catalog-base-search" class="chat-next-sr-only">搜索知识库</label><Input id="catalog-base-search" v-model:value="keyword" placeholder="名称、描述或向量模型" /><Button html-type="submit" :disabled="pending">搜索</Button><Button v-if="query.keyword" :disabled="pending" @click="setQuery(1,'')">清除筛选</Button><Button :loading="loading" :disabled="pending || loading" @click="resource.load()">刷新</Button></form>
    <Alert v-if="error" type="error" show-icon :message="data?'刷新失败，仍显示上次列表':'知识库列表读取失败'" :description="error"><template #action><Button :disabled="loading || pending" @click="resource.load()">重试列表</Button></template></Alert>
    <Skeleton v-if="loading && !data" active />
    <template v-if="data"><Empty v-if="!list.records.length" :description="query.keyword?'没有匹配知识库':'暂无知识库'" />
      <div v-else class="admin-next-catalog-list" aria-label="知识库列表"><div class="admin-next-catalog-list-head admin-next-catalog-base-row" aria-hidden="true"><span>知识库</span><span>文档 / 可检索</span><span>状态</span><span>操作</span></div><article v-for="item in list.records" :key="item.id" class="admin-next-catalog-base-row"><div><Button type="link" class="admin-next-catalog-name" :disabled="pending" @click="openEditor(item,'view',$event)">{{ item.baseName }}</Button><p v-if="item.description" class="admin-next-catalog-description">{{ item.description }}</p><span class="admin-next-catalog-subtle">{{ item.embeddingModel || '全局向量模型' }}</span></div><div><span class="admin-next-catalog-mobile-label">文档 / 可检索</span>{{ item.documentCount ?? '—' }} / {{ item.retrievableDocumentCount ?? '—' }}</div><div><span class="admin-next-catalog-tag">{{ String(item.isDefault)==='1'?'默认知识库':'普通' }}</span></div><div class="admin-next-catalog-row-actions"><Button :disabled="pending || loading" @click="openEditor(item,'edit',$event)">编辑</Button><Button danger :disabled="pending || loading" :aria-label="`删除知识库 ${item.baseName}`" @click="remove(item,$event)">删除</Button></div></article></div>
      <nav class="admin-next-catalog-pagination" aria-label="知识库分页"><span>共 {{ list.total }} 个 · 第 {{ list.page }} / {{ list.totalPages }} 页</span><div><Button :disabled="list.page<=1 || pending" @click="setQuery(list.page-1)">上一页</Button><Button :disabled="list.page>=list.totalPages || pending" @click="setQuery(list.page+1)">下一页</Button></div></nav>
    </template>
    <BaseEditor :open="editorOpen" :draft="draft" :mode="mode" :pending="pending" :error="actionError" @close="editorOpen=false" @save="save" @edit="mode='edit'" @after-close="restoreFocus" />
    <Modal :open="Boolean(target)" title="删除知识库" ok-text="确认停用" cancel-text="取消" :ok-button-props="{danger:true,loading:pending,disabled:pending}" :cancel-button-props="{disabled:pending}" :closable="!pending" :keyboard="!pending" :mask-closable="!pending" :get-container="popupContainer" centered destroy-on-close @cancel="target=null" @ok="confirmDelete" @after-close="restoreFocus"><template v-if="target"><p>停用“{{ target.baseName }}”？</p><p class="admin-next-catalog-id">ID：{{ target.id }}</p><p>停用后不可再选择此知识库；已有文档不会被此操作删除。</p><Alert v-if="actionError" type="error" show-icon :message="actionError" /></template></Modal>
  </section>
</template>
