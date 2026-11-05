<script setup>
import { computed,inject,nextTick,onBeforeUnmount,onMounted,reactive,ref } from 'vue'
import { Button,Input,Modal } from 'ant-design-vue'
import { buildRouteWrite } from './routeModel.js'
import { routeApi } from './routeApi.js'
const props=defineProps({kind:String,initial:Object,context:Object}),emit=defineEmits(['close','saved'])
const draft=reactive({...props.initial,operatorId:''}),error=ref(''),pending=ref(null),busy=ref(false),container=inject('admin-next-container')
let alive=true;const controller=new AbortController();onBeforeUnmount(()=>{alive=false;controller.abort()})
onMounted(async()=>{await nextTick();if(alive)document.querySelector('.admin-next-route-editor-dialog')?.focus()})
const actionNames={scope:'保存范围',topic:'保存主题',deleteScope:'删除范围',deleteTopic:'删除主题',relation:'保存关联',removeRelation:'移除关联',profile:'重新生成画像',batch:'批量重建画像'}
const labels={scopeName:'范围名称',parentScopeId:'父范围',description:'说明',aliases:'别名',examples:'示例',sortOrder:'排序',topicName:'主题名称',scopeId:'所属范围',answerShape:'回答结构',executionPreference:'执行偏好',topicId:'主题',documentId:'文档',relationScore:'关联分数',relationSource:'关联来源',reason:'关联说明',operatorId:'操作人 ID'}
const fields=computed(()=>props.kind==='scope'?['scopeName','parentScopeId','description','aliases','examples','sortOrder','operatorId']:props.kind==='topic'?['topicName','scopeId','description','aliases','examples','answerShape','executionPreference','sortOrder','operatorId']:props.kind==='relation'?['topicId','documentId','relationScore','relationSource','reason','operatorId']:['operatorId'])
const options=key=>key==='scopeId'||key==='parentScopeId'?props.context.scopes.filter(x=>x.id!==draft.id).map(x=>[x.id,x.scopeName]):key==='topicId'?props.context.topics.map(x=>[x.id,x.topicName]):props.context.documents.map(x=>[x.documentId,x.documentName])
function prepare(){if(busy.value)return;try{pending.value=buildRouteWrite(props.kind,draft,props.context);error.value=''}catch(e){error.value=e.message}}
async function save(){if(busy.value||!pending.value)return;busy.value=true;try{await routeApi.write(props.kind,pending.value,controller.signal);if(alive)emit('saved')}catch(e){if(alive){error.value=e.message;pending.value=null}}finally{if(alive)busy.value=false}}
</script>
<template><Modal wrap-class-name="admin-next-route-editor-dialog" :open="true" title="路由配置操作" :get-container="container" :closable="!busy" :mask-closable="false" :keyboard="!busy" @cancel="emit('close')"><p v-if="error" role="alert">{{ error }}</p><form class="admin-next-route-form" @submit.prevent="prepare"><div v-for="key in fields" :key="key"><label :for="'route-'+key">{{ labels[key] }}</label><select v-if="['scopeId','parentScopeId','topicId','documentId'].includes(key)" :id="'route-'+key" v-model="draft[key]"><option value="">请选择</option><option v-for="[id,name] in options(key)" :key="id" :value="id">{{ name }}</option></select><Input v-else :id="'route-'+key" v-model:value="draft[key]" :disabled="busy" /></div><p v-if="['batch','profile'].includes(kind)">重建文档：{{ (initial.documentIds||[initial.documentId]).join('、') }}</p></form><template #footer><Button :disabled="busy" @click="emit('close')">取消</Button><Button type="primary" :disabled="busy" @click="prepare">检查并确认</Button></template></Modal><Modal :open="Boolean(pending)" title="确认操作" :get-container="container" :confirm-loading="busy" :closable="!busy" :mask-closable="false" :keyboard="!busy" @cancel="!busy&&(pending=null)" @ok="save" :ok-button-props="{disabled:busy}" :cancel-button-props="{disabled:busy}"><p>知识库：{{ context.knowledgeBaseId }}</p><p>操作：{{ actionNames[kind] }}</p><p>对象：{{ pending?.id||pending?.documentId||pending?.topicId||initial.scopeName||initial.topicName||'新建配置' }}</p><p v-if="pending?.documentIds">文档集合：{{ pending.documentIds.join('、') }}</p><p v-if="pending?.topicId">主题：{{ pending.topicId }}</p><p v-if="pending?.documentId">文档：{{ pending.documentId }}</p><p>操作人：{{ pending?.operatorId }}</p></Modal></template>




