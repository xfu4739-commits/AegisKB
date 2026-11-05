<script setup>
import { inject } from 'vue'
import { Alert, Button, Input, Modal } from 'ant-design-vue'
import { configGroups, getField, setField } from './catalogSchema.js'
defineProps({ open:Boolean, draft:Object, mode:String, pending:Boolean, error:String })
defineEmits(['close','save','edit','afterClose'])
const popupContainer=inject('admin-next-container')
const controlId=field=>`base-${field.root}-${field.path.join('-')}`
function boolValue(configs,field){const value=getField(configs,field);return value==null?'':['true','1'].includes(String(value))?'true':'false'}
</script>
<template>
  <Modal :open="open" :title="mode==='create'?'新建知识库':mode==='view'?'知识库详情':'编辑知识库'" :width="760" :get-container="popupContainer" :closable="!pending" :keyboard="!pending" :mask-closable="!pending" destroy-on-close @cancel="$emit('close')" @after-close="$emit('afterClose')">
    <Alert v-if="error" type="error" show-icon :message="error" role="alert" />
    <form v-if="draft" class="admin-next-catalog-form" @submit.prevent="$emit('save')">
      <p v-if="draft.id" class="admin-next-catalog-id">ID：{{ draft.id }}</p>
      <fieldset :disabled="pending || mode==='view'">
        <div class="admin-next-catalog-field"><label for="base-name">知识库名称</label><Input id="base-name" v-model:value="draft.baseName" :maxlength="200" autocomplete="off" /></div>
        <div class="admin-next-catalog-field"><label for="base-description">描述</label><textarea id="base-description" v-model="draft.description" rows="3" /></div>
        <div class="admin-next-catalog-grid"><div class="admin-next-catalog-field"><label for="base-model">向量模型</label><Input id="base-model" v-model:value="draft.embeddingModel" placeholder="沿用全局配置" /></div><div class="admin-next-catalog-field"><label for="base-sort">排序</label><input id="base-sort" v-model="draft.sortOrder" type="number" step="1" /></div></div>
        <label class="admin-next-catalog-check"><input v-model="draft.isDefault" type="checkbox" />设为默认知识库（替换当前默认项）</label>
        <details v-for="group in configGroups" :key="group.title" class="admin-next-catalog-config"><summary>{{ group.title }}</summary><div class="admin-next-catalog-grid"><div v-for="field in group.fields" :key="controlId(field)" class="admin-next-catalog-field"><label :for="controlId(field)">{{ field.label }}</label><select v-if="field.boolean" :id="controlId(field)" :value="boolValue(draft.configs,field)" @change="setField(draft.configs,field,$event.target.value===''?undefined:$event.target.value==='true')"><option value="">未设置</option><option value="true">启用</option><option value="false">关闭</option></select><input v-else :id="controlId(field)" type="number" :min="field.min" :step="field.integer?1:'any'" :value="getField(draft.configs,field) ?? ''" placeholder="未设置" @input="setField(draft.configs,field,$event.target.value)" /></div></div></details>
        <details class="admin-next-catalog-config"><summary>扩展配置</summary><div class="admin-next-catalog-field"><label for="base-metadata">元数据过滤 JSON</label><textarea id="base-metadata" v-model="draft.metadataFilterJson" rows="4" spellcheck="false" /></div><details><summary>查看完整配置</summary><pre>{{ JSON.stringify(draft.configs,null,2) }}</pre></details></details>
      </fieldset>
    </form>
    <template #footer><Button :disabled="pending" @click="$emit('close')">{{ mode==='view'?'关闭':'取消' }}</Button><Button v-if="mode==='view'" type="primary" @click="$emit('edit')">编辑</Button><Button v-else type="primary" :loading="pending" :disabled="pending" @click="$emit('save')">保存</Button></template>
  </Modal>
</template>
