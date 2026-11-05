<script setup>
import { Button,Empty,Input } from 'ant-design-vue'
import { useRouteConfiguration } from './routeState.js'
import Facts from '../document/Facts.vue'
import ReadState from '../document/ReadState.vue'
import RouteEditor from './RouteEditor.vue'
import './route.css'
const {kb,tab,scope,topic,keyword,selected,editor,bases,scopes,topics,relations,documents,profile,docs,context,availableTopics,rows,page,visible,ready,read,open,closeEditor,saved,viewProfile,navigate,switchBase,search}=useRouteConfiguration()
</script>
<template>
<section class="admin-next-route">
 <header class="admin-next-route-heading"><h1>知识路由</h1><RouterLink to="/admin/knowledge-route/traces">查看追踪</RouterLink></header>
 <div class="admin-next-route-toolbar"><label for="route-base">知识库</label><select id="route-base" :value="kb" @change="switchBase($event.target.value)"><option value="">请选择知识库</option><option v-for="base in bases.data.value||[]" :key="base.id" :value="base.id">{{ base.baseName }}</option></select><Button v-if="kb" @click="read">刷新配置</Button></div>
 <ReadState :state="bases" label="知识库" @retry="bases.read({})" />
 <Empty v-if="!kb" description="选择知识库后查看配置" />
 <template v-else>
  <nav aria-label="路由配置分类"><Button v-for="[value,label] in [['scopes','知识范围'],['topics','主题'],['profiles','文档画像'],['relations','文档关联']]" :key="value" :type="tab===value?'primary':'text'" :aria-current="tab===value?'page':undefined" @click="navigate({tab:value,page:'1',q:undefined})">{{ label }}</Button></nav>
  <form class="admin-next-route-toolbar" @submit.prevent="search">
   <label for="route-search" class="chat-next-sr-only">搜索当前列表</label><Input id="route-search" v-model:value="keyword" placeholder="搜索当前列表" />
   <template v-if="tab==='topics'||tab==='relations'"><label for="route-filter-scope">范围</label><select id="route-filter-scope" :value="scope" @change="navigate({scope:$event.target.value,topic:undefined,page:'1'})"><option value="">全部范围</option><option v-for="x in context.scopes" :key="x.id" :value="x.id">{{ x.scopeName }}</option></select></template>
   <template v-if="tab==='relations'"><label for="route-filter-topic">主题</label><select id="route-filter-topic" :value="topic" @change="navigate({topic:$event.target.value,page:'1'})"><option value="">全部主题</option><option v-for="x in availableTopics" :key="x.id" :value="x.id">{{ x.topicName }}</option></select></template><Button html-type="submit">筛选</Button>
  </form>
  <ReadState :state="scopes" label="范围" @retry="read" /><ReadState :state="topics" label="主题" @retry="read" />
  <section v-if="tab==='scopes'||tab==='topics'" class="admin-next-route-panel">
   <div class="admin-next-route-heading"><h2>{{ tab==='scopes'?'知识范围':'主题' }}</h2><Button type="primary" :disabled="!ready" @click="open(tab==='scopes'?'scope':'topic',{scopeId:scope})">{{ tab==='scopes'?'新建范围':'新建主题' }}</Button></div>
   <ul><li v-for="item in visible" :key="item.id"><div><strong>{{ item.scopeName||item.topicName }}</strong><p>{{ item.description||'—' }}</p><span v-if="item.scopeId">{{ context.scopes.find(x=>x.id===item.scopeId)?.scopeName||item.scopeId }}</span><details><summary>配置详情</summary><Facts :value="item" /></details></div><div class="admin-next-route-actions"><Button :disabled="!ready" @click="open(tab==='scopes'?'scope':'topic',item)">编辑</Button><Button danger :disabled="!ready" @click="open(tab==='scopes'?'deleteScope':'deleteTopic',item)">删除</Button></div></li></ul>
  </section>
  <section v-if="tab==='profiles'" class="admin-next-route-panel">
   <h2>文档画像</h2><ReadState :state="documents" label="文档" @retry="read" /><p class="admin-next-route-muted">当前知识库在全局前 200 份文档中的已加载集合，共 {{ docs.length }} 份；不代表全库。</p>
   <div class="admin-next-route-actions"><Button :disabled="!ready||!docs.length" @click="open('batch',{documentIds:docs.map(x=>x.documentId)})">重建已加载画像</Button><Button :disabled="!ready||!selected.length" @click="open('batch',{documentIds:[...selected]})">重建选中画像</Button><span>已选 {{ selected.length }} 份</span></div>
   <ul><li v-for="item in visible" :key="item.documentId"><label><input v-model="selected" type="checkbox" :value="item.documentId">{{ item.documentName }}</label><div class="admin-next-route-actions"><Button @click="viewProfile(item.documentId)">查看画像</Button><Button :disabled="!ready" @click="open('profile',{documentId:item.documentId})">重新生成</Button></div></li></ul>
   <ReadState :state="profile" label="画像" @retry="viewProfile($route.query.doc);profile.read({documentId:$route.query.doc})" /><section v-if="profile.data.value" aria-label="文档画像详情"><h3>画像详情</h3><Facts :value="profile.data.value" /><Button @click="navigate({doc:undefined})">返回文档列表</Button></section>
  </section>
  <section v-if="tab==='relations'" class="admin-next-route-panel">
   <div class="admin-next-route-heading"><h2>主题文档关联</h2><Button type="primary" :disabled="!ready" @click="open('relation',{relationScore:'0.9',relationSource:'manual',topicId:topic})">新增关联</Button></div><ReadState :state="relations" label="关联" @retry="read" />
   <ul><li v-for="item in visible" :key="item.topicId+':'+item.documentId"><div><strong>{{ item.topicName||item.topicId }} · {{ item.documentName||item.documentId }}</strong><p>分数 {{ item.relationScore??'—' }} · {{ item.relationSource||'—' }}</p><p>{{ item.reason }}</p></div><div class="admin-next-route-actions"><Button :disabled="!ready||!docs.some(x=>x.documentId===item.documentId)" @click="open('relation',item)">编辑关联</Button><Button danger :disabled="!ready" @click="open('removeRelation',item)">移除关联</Button></div></li></ul>
  </section>
  <Empty v-if="!rows.length&&ready" description="暂无匹配数据" />
  <nav aria-label="配置分页"><Button :disabled="page<=1" @click="navigate({page:String(page-1)})">上一页配置</Button><span>第 {{ page }} 页 · {{ rows.length }} 条已加载结果</span><Button :disabled="page*20>=rows.length" @click="navigate({page:String(page+1)})">下一页配置</Button></nav>
  <RouteEditor v-if="editor" :kind="editor.kind" :initial="editor.initial" :context="editor.context" @close="closeEditor" @saved="saved" />
 </template>
</section>
</template>
